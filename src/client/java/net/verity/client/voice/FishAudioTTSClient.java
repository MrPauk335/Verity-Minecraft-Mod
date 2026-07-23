package net.verity.client.voice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.verity.VerityMod;
import net.verity.client.config.VerityClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;

public class FishAudioTTSClient {

    private static final Gson GSON = new Gson();
    private static final String API_URL = "https://api.fish.audio/v1/tts";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Path TTS_CACHE_DIR = Path.of("verity_tts_cache");

    public static void speakAsync(String text) {
        speakAsync(text, -1, 0, 0, 0);
    }

    public static void speakAsync(String text, int entityId, double x, double y, double z) {
        if (!VerityClientConfig.ttsEnabled()) return;
        List<String> keys = VerityClientConfig.ttsApiKeys();
        if (keys.isEmpty()) {
            VerityMod.LOGGER.warn("TTS: No Fish Audio API keys");
            return;
        }

        boolean isDark = text.contains("\u00a7c") || text.contains("\u00a74");
        boolean isMonster = text.contains("\u00a74");
        boolean isCountdown = text.contains("\u00a7c");
        float speed = VerityClientConfig.ttsSpeed();
        if (isMonster) speed = 0.85f;
        else if (isCountdown) speed = 0.9f;

        String cleanText = stripMinecraftFormatting(text);
        if (cleanText.isBlank() || cleanText.length() < 2) return;
        if (cleanText.length() > 500) cleanText = cleanText.substring(0, 500);

        cleanText = addEmotionTags(cleanText, isMonster, isCountdown);

        final String finalText = cleanText;
        final float finalSpeed = speed;

        CompletableFuture.supplyAsync(() -> {
            for (String apiKey : keys) {
                try {
                    byte[] result = synthesize(finalText, apiKey, finalSpeed);
                    if (result != null) return result;
                } catch (Exception e) {
                    VerityMod.LOGGER.warn("TTS failed with key {}...: {}",
                            apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey, e.getMessage());
                }
            }
            return null;
        }).thenAccept(wavData -> {
            if (wavData != null && wavData.length > 0) {
                Minecraft.getInstance().execute(() -> playWav(wavData, finalText, entityId, x, y, z));
            }
        });
    }

    private static String addEmotionTags(String text, boolean isMonster, boolean isCountdown) {
        if (isMonster) {
            if (text.contains("!") || text.toUpperCase().equals(text)) {
                return "[angry] " + text;
            }
            return "[groaning] " + text + " [panting]";
        } else if (isCountdown) {
            if (text.contains("...") || text.length() < 20) {
                return "[whispering] " + text + " [long pause]";
            }
            return "[whispering] " + text + " [pause]";
        }
        if (text.contains("?")) {
            return "[soft] " + text;
        }
        if (text.contains("!")) {
            return "[excited] " + text;
        }
        return text;
    }

    private static byte[] synthesize(String text, String apiKey, float speed) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("reference_id", VerityClientConfig.ttsVoiceId());
        body.addProperty("format", "wav");
        body.addProperty("speed", speed);
        body.addProperty("normalize", true);

        if (speed < 1.0f) {
            body.addProperty("temperature", 0.5);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("model", VerityClientConfig.ttsModel())
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<byte[]> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                VerityMod.LOGGER.warn("TTS API returned status {} (Attempt {}/3)", response.statusCode(), attempt);
            } catch (Exception e) {
                VerityMod.LOGGER.warn("TTS request failed (Attempt {}/3): {}", attempt, e.getMessage());
            }
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    private static void playWav(final byte[] rawWavData, final String text, final int entityId, final double x, final double y, final double z) {
        Thread playbackThread = new Thread(() -> {
            int alBuffer = 0;
            int alSource = 0;
            try {
                if (rawWavData == null || rawWavData.length < 44) {
                    VerityMod.LOGGER.error("TTS: WAV data too short ({} bytes)", rawWavData == null ? 0 : rawWavData.length);
                    return;
                }

                byte[] currentWav = rawWavData;
                int audioFormat   = readInt16LE(currentWav, 20);
                int channels      = readInt16LE(currentWav, 22);
                int sampleRate    = readInt32LE(currentWav, 24);
                int bitsPerSample = readInt16LE(currentWav, 34);

                int dataOffset = 12;
                int dataSize   = 0;
                while (dataOffset + 8 <= currentWav.length) {
                    String chunkId = new String(currentWav, dataOffset, 4, java.nio.charset.StandardCharsets.US_ASCII);
                    int    chunkSz = readInt32LE(currentWav, dataOffset + 4);

                    if (chunkSz < 0 || dataOffset + 8 + chunkSz > currentWav.length) {
                        break;
                    }

                    if ("data".equals(chunkId)) {
                        dataSize = chunkSz;
                        dataOffset += 8;
                        break;
                    }

                    int paddedSize = (chunkSz + 1) & ~1;
                    dataOffset += 8 + paddedSize;
                }

                if (dataSize <= 0 || dataOffset + dataSize > currentWav.length) {
                    dataOffset = 44;
                    dataSize   = Math.max(0, currentWav.length - 44);
                }

                if (dataSize == 0) {
                    VerityMod.LOGGER.error("TTS: WAV data size is 0, cannot play");
                    return;
                }

                boolean is3D = (entityId != -1) || (x != 0 || y != 0 || z != 0);

                if (channels == 2 && is3D && bitsPerSample == 16) {
                    int sampleCount = dataSize / 4;
                    byte[] monoPcm = new byte[sampleCount * 2];
                    for (int i = 0; i < sampleCount; i++) {
                        int idx = dataOffset + i * 4;
                        short left  = (short) ((currentWav[idx] & 0xFF) | (currentWav[idx + 1] << 8));
                        short right = (short) ((currentWav[idx + 2] & 0xFF) | (currentWav[idx + 3] << 8));
                        short mono  = (short) ((left + right) / 2);
                        monoPcm[i * 2]     = (byte) (mono & 0xFF);
                        monoPcm[i * 2 + 1] = (byte) ((mono >> 8) & 0xFF);
                    }
                    currentWav = monoPcm;
                    dataOffset = 0;
                    dataSize = monoPcm.length;
                    channels = 1;
                }

                int alFormat;
                if (channels == 1 && bitsPerSample == 8)       alFormat = org.lwjgl.openal.AL10.AL_FORMAT_MONO8;
                else if (channels == 1 && bitsPerSample == 16) alFormat = org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
                else if (channels == 2 && bitsPerSample == 8)  alFormat = org.lwjgl.openal.AL10.AL_FORMAT_STEREO8;
                else                                            alFormat = org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;

                int estimatedDurationSec = Math.max(1, dataSize / (sampleRate * channels * bitsPerSample / 8));
                VerityMod.LOGGER.info("TTS: Playing WAV ({} bytes, {}Hz {}ch {}bit 3D={}, ~{}s) for: {}",
                        currentWav.length, sampleRate, channels, bitsPerSample, is3D, estimatedDurationSec,
                        text.substring(0, Math.min(50, text.length())));

                java.nio.ByteBuffer pcmBuffer = java.nio.ByteBuffer
                        .allocateDirect(dataSize)
                        .order(java.nio.ByteOrder.nativeOrder());
                pcmBuffer.put(currentWav, dataOffset, dataSize);
                pcmBuffer.flip();

                alBuffer = org.lwjgl.openal.AL10.alGenBuffers();
                org.lwjgl.openal.AL10.alBufferData(alBuffer, alFormat, pcmBuffer, sampleRate);

                alSource = org.lwjgl.openal.AL10.alGenSources();
                org.lwjgl.openal.AL10.alSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFER, alBuffer);
                org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_GAIN, VerityClientConfig.ttsVolume());

                if (is3D) {
                    org.lwjgl.openal.AL10.alSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE, org.lwjgl.openal.AL10.AL_TRUE);
                    org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_REFERENCE_DISTANCE, 2.0f);
                    org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_MAX_DISTANCE, 48.0f);
                    org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_ROLLOFF_FACTOR, 1.0f);
                    update3dSourcePosition(alSource, entityId, x, y, z);
                } else {
                    org.lwjgl.openal.AL10.alSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE, org.lwjgl.openal.AL10.AL_TRUE);
                    org.lwjgl.openal.AL10.alSource3f(alSource, org.lwjgl.openal.AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
                }

                org.lwjgl.openal.AL10.alSourcePlay(alSource);
                net.verity.client.render.VerityItemRenderer.setTalking(true);

                int state;
                do {
                    if (is3D) {
                        update3dSourcePosition(alSource, entityId, x, y, z);
                    }
                    Thread.sleep(50);
                    state = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
                } while (state == org.lwjgl.openal.AL10.AL_PLAYING);

            } catch (Exception e) {
                VerityMod.LOGGER.error("TTS: OpenAL playback error: {}", e.getMessage());
            } finally {
                net.verity.client.render.VerityItemRenderer.setTalking(false);
                try {
                    if (alSource != 0) org.lwjgl.openal.AL10.alDeleteSources(alSource);
                    if (alBuffer != 0) org.lwjgl.openal.AL10.alDeleteBuffers(alBuffer);
                } catch (Exception ignored) {}
            }
        }, "Verity-TTS-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private static void update3dSourcePosition(int alSource, int entityId, double defaultX, double defaultY, double defaultZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return;
        }

        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 camPos = camera.getPosition();

        double tx = defaultX;
        double ty = defaultY;
        double tz = defaultZ;

        if (mc.level != null && entityId != -1) {
            net.minecraft.world.entity.Entity ent = mc.level.getEntity(entityId);
            if (ent != null) {
                tx = ent.getX();
                ty = ent.getEyeY();
                tz = ent.getZ();
            }
        }

        double dx = tx - camPos.x;
        double dy = ty - camPos.y;
        double dz = tz - camPos.z;

        float yawRad   = (float) Math.toRadians(camera.getYRot());
        float pitchRad = (float) Math.toRadians(camera.getXRot());

        float fwdX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float fwdY = (float) (-Math.sin(pitchRad));
        float fwdZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        float rightX = (float) (-Math.cos(yawRad));
        float rightY = 0.0f;
        float rightZ = (float) (-Math.sin(yawRad));

        float upX = fwdY * rightZ - fwdZ * rightY;
        float upY = fwdZ * rightX - fwdX * rightZ;
        float upZ = fwdX * rightY - fwdY * rightX;

        float localX = (float) (dx * rightX + dy * rightY + dz * rightZ);
        float localY = (float) (dx * upX    + dy * upY    + dz * upZ);
        float localZ = (float) -(dx * fwdX   + dy * fwdY   + dz * fwdZ);

        org.lwjgl.openal.AL10.alSource3f(alSource, org.lwjgl.openal.AL10.AL_POSITION, localX, localY, localZ);
    }

    private static int readInt16LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int readInt32LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8)
             | ((b[off+2] & 0xFF) << 16) | ((b[off+3] & 0xFF) << 24);
    }

    private static String stripMinecraftFormatting(String text) {
        String result = text.replaceAll("\u00a7[0-9a-fklmnor]", "");
        result = result.replaceAll("<Verity[^>]*>", "");
        result = result.replaceAll("<Verity\u2122>", "");
        result = result.replaceAll("\u266a.*", "");
        result = result.replaceAll("\\*[^*]+\\*", "");
        result = result.replaceAll("\\n{2,}", " ").trim();
        return result;
    }
}