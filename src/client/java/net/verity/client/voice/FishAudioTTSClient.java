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
        if (!VerityClientConfig.ttsEnabled()) return;
        List<String> keys = VerityClientConfig.ttsApiKeys();
        if (keys.isEmpty()) {
            VerityMod.LOGGER.warn("TTS: No Fish Audio API keys");
            return;
        }

        // Определяем фазу по цвету сообщения
        // §e = HELPER/OMNISCIENT/POSSESSIVE (normal), §c = COUNTDOWN, §4 = MONSTER/HUNTER
        boolean isDark = text.contains("\u00a7c") || text.contains("\u00a74");
        boolean isMonster = text.contains("\u00a74");
        boolean isCountdown = text.contains("\u00a7c");
        float speed = VerityClientConfig.ttsSpeed();
        if (isMonster) speed = 0.85f;
        else if (isCountdown) speed = 0.9f;

        String cleanText = stripMinecraftFormatting(text);
        if (cleanText.isBlank() || cleanText.length() < 2) return;
        if (cleanText.length() > 500) cleanText = cleanText.substring(0, 500);

        // Добавляем эмоциональные теги Fish Audio по фазе
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
                Minecraft.getInstance().execute(() -> playWav(wavData, finalText));
            }
        });
    }

    /**
     * Добавляет эмоциональные теги Fish Audio в текст в зависимости от фазы.
     */
    private static String addEmotionTags(String text, boolean isMonster, boolean isCountdown) {
        if (isMonster) {
            // MONSTER/HUNTER — яростный, отчаянный
            if (text.contains("!") || text.toUpperCase().equals(text)) {
                return "[angry] " + text;
            }
            return "[groaning] " + text + " [panting]";
        } else if (isCountdown) {
            // COUNTDOWN — жуткий шёпот, паузы
            if (text.contains("...") || text.length() < 20) {
                return "[whispering] " + text + " [long pause]";
            }
            return "[whispering] " + text + " [pause]";
        }
        // HELPER/OMNISCIENT/POSSESSIVE — нормальный, иногда мягкий
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
        body.addProperty("format", "wav");   // WAV → не нужен MP3-декодер, читаем PCM напрямую
        body.addProperty("speed", speed);
        body.addProperty("normalize", true);

        // Для тёмных фаз — ниже температура (стабильнее, жутче)
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

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            String errBody = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            VerityMod.LOGGER.warn("TTS API returned {}: {}", response.statusCode(),
                    errBody.substring(0, Math.min(200, errBody.length())));
            return null;
        }

        return response.body();
    }

    /**
     * Воспроизводит WAV через OpenAL (LWJGL — уже встроен в Minecraft).
     * Работает на Windows / Linux / macOS / FreeBSD / Android (Zalith).
     * Никаких внешних процессов, никаких зависимостей.
     */
    private static void playWav(byte[] wavData, String text) {
        Thread playbackThread = new Thread(() -> {
            int alBuffer = 0;
            int alSource = 0;
            try {
                // ── 1. Разбираем WAV-заголовок ───────────────────────────────
                // Минимальная длина: 44 байта (стандартный PCM WAV header)
                if (wavData == null || wavData.length < 44) {
                    VerityMod.LOGGER.error("TTS: WAV data too short ({} bytes)", wavData == null ? 0 : wavData.length);
                    return;
                }

                // Читаем поля из little-endian заголовка
                int audioFormat  = readInt16LE(wavData, 20); // 1 = PCM
                int channels     = readInt16LE(wavData, 22); // 1=mono, 2=stereo
                int sampleRate   = readInt32LE(wavData, 24);
                int bitsPerSample= readInt16LE(wavData, 34); // 8 или 16

                // Ищем chunk "data" (может быть не на 36, если есть LIST/fact chunks)
                int dataOffset = 12;
                int dataSize   = 0;
                while (dataOffset + 8 <= wavData.length) {
                    String chunkId = new String(wavData, dataOffset, 4, java.nio.charset.StandardCharsets.US_ASCII);
                    int    chunkSz = readInt32LE(wavData, dataOffset + 4);
                    
                    // Safety check to prevent negative chunk size or array index out of bounds
                    if (chunkSz < 0 || dataOffset + 8 + chunkSz > wavData.length) {
                        break;
                    }
                    
                    if ("data".equals(chunkId)) {
                        dataSize = chunkSz;
                        dataOffset += 8;
                        break;
                    }
                    
                    // Align chunk size to 2-byte boundary
                    int paddedSize = (chunkSz + 1) & ~1;
                    dataOffset += 8 + paddedSize;
                }

                if (dataSize <= 0 || dataOffset + dataSize > wavData.length) {
                    // Fallback: предположим стандартный 44-байтный header
                    dataOffset = 44;
                    dataSize   = Math.max(0, wavData.length - 44);
                }

                if (dataSize == 0) {
                    VerityMod.LOGGER.error("TTS: WAV data size is 0, cannot play");
                    return;
                }

                // Определяем формат OpenAL
                int alFormat;
                if (channels == 1 && bitsPerSample == 8)  alFormat = org.lwjgl.openal.AL10.AL_FORMAT_MONO8;
                else if (channels == 1 && bitsPerSample == 16) alFormat = org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
                else if (channels == 2 && bitsPerSample == 8)  alFormat = org.lwjgl.openal.AL10.AL_FORMAT_STEREO8;
                else                                            alFormat = org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;

                int estimatedDurationSec = Math.max(1, dataSize / (sampleRate * channels * bitsPerSample / 8));
                VerityMod.LOGGER.info("TTS: Playing WAV ({} bytes, {}Hz {}ch {}bit, ~{}s) for: {}",
                        wavData.length, sampleRate, channels, bitsPerSample, estimatedDurationSec,
                        text.substring(0, Math.min(50, text.length())));

                // ── 2. Загружаем PCM в OpenAL буфер ─────────────────────────
                java.nio.ByteBuffer pcmBuffer = java.nio.ByteBuffer
                        .allocateDirect(dataSize)
                        .order(java.nio.ByteOrder.nativeOrder());
                pcmBuffer.put(wavData, dataOffset, dataSize);
                pcmBuffer.flip();

                alBuffer = org.lwjgl.openal.AL10.alGenBuffers();
                org.lwjgl.openal.AL10.alBufferData(alBuffer, alFormat, pcmBuffer, sampleRate);

                alSource = org.lwjgl.openal.AL10.alGenSources();
                org.lwjgl.openal.AL10.alSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFER, alBuffer);
                org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_GAIN, 1.0f);

                // ── 3. Играем ────────────────────────────────────────────────
                org.lwjgl.openal.AL10.alSourcePlay(alSource);

                // Аудио начало играть — открываем рот
                net.verity.client.render.VerityItemRenderer.setTalking(true);

                // Ждём окончания (polling каждые 50мс)
                int state;
                do {
                    Thread.sleep(50);
                    state = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
                } while (state == org.lwjgl.openal.AL10.AL_PLAYING);

            } catch (Exception e) {
                VerityMod.LOGGER.error("TTS: OpenAL playback error: {}", e.getMessage());
            } finally {
                // Закрываем рот и освобождаем ресурсы OpenAL
                net.verity.client.render.VerityItemRenderer.setTalking(false);
                try {
                    if (alSource  != 0) org.lwjgl.openal.AL10.alDeleteSources(alSource);
                    if (alBuffer  != 0) org.lwjgl.openal.AL10.alDeleteBuffers(alBuffer);
                } catch (Exception ignored) {}
            }
        }, "Verity-TTS-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    // ── WAV-хелперы ──────────────────────────────────────────────────────────

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
        result = result.replaceAll("\\*[^*]+\\*", ""); // убрать *действия*
        result = result.replaceAll("\\n{2,}", " ").trim();
        return result;
    }
}
