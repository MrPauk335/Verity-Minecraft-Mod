package net.verity.client.voice;

import net.verity.VerityMod;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class VerityMusicClient {

    private static volatile int currentSource = 0;
    private static volatile int currentBuffer = 0;

    public static void stopMusic() {
        int src = currentSource;
        int buf = currentBuffer;
        currentSource = 0;
        currentBuffer = 0;
        if (src != 0) {
            try { AL10.alSourceStop(src); } catch (Exception ignored) {}
            try { AL10.alDeleteSources(src); } catch (Exception ignored) {}
        }
        if (buf != 0) {
            try { AL10.alDeleteBuffers(buf); } catch (Exception ignored) {}
        }
    }

    public static void playOggResource(String soundName, float volume, float pitch) {
        playOggResource(soundName, volume, pitch, true);
    }

    public static void playOggResource(String soundName, float volume, float pitch, boolean looping) {
        stopMusic();
        VerityMod.LOGGER.info("Music: playOggResource called — name='{}', vol={}, pitch={}, looping={}", soundName, volume, pitch, looping);

        Thread t = new Thread(() -> {
            int alBuffer = 0;
            int alSource = 0;
            try {
                byte[] oggData = loadOggBytes(soundName);
                if (oggData == null || oggData.length == 0) {
                    VerityMod.LOGGER.error("Music: FAILED to load OGG bytes for '{}'", soundName);
                    return;
                }
                VerityMod.LOGGER.info("Music: Loaded {} bytes for '{}'", oggData.length, soundName);

                alBuffer = decodeOggToBuffer(oggData);
                if (alBuffer == 0) {
                    VerityMod.LOGGER.error("Music: FAILED to decode OGG '{}'", soundName);
                    return;
                }

                alSource = AL10.alGenSources();
                int err = AL10.alGetError();
                if (err != AL10.AL_NO_ERROR) {
                    VerityMod.LOGGER.error("Music: alGenSources error: 0x{}", Integer.toHexString(err));
                    return;
                }

                AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
                AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
                AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);
                AL10.alSourcei(alSource, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);

                err = AL10.alGetError();
                if (err != AL10.AL_NO_ERROR) {
                    VerityMod.LOGGER.error("Music: alSource setup error: 0x{}", Integer.toHexString(err));
                    return;
                }

                currentSource = alSource;
                currentBuffer = alBuffer;

                AL10.alSourcePlay(alSource);
                err = AL10.alGetError();
                if (err != AL10.AL_NO_ERROR) {
                    VerityMod.LOGGER.error("Music: alSourcePlay error: 0x{}", Integer.toHexString(err));
                    return;
                }

                VerityMod.LOGGER.info("Music: PLAYING '{}'", soundName);

                int state;
                do {
                    Thread.sleep(100);
                    state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
                } while (state == AL10.AL_PLAYING && currentSource == alSource);

                VerityMod.LOGGER.info("Music: Finished '{}' (state={})", soundName, state);

            } catch (Exception e) {
                VerityMod.LOGGER.error("Music: EXCEPTION — {}", e.toString(), e);
            } finally {
                if (currentSource == alSource) {
                    currentSource = 0;
                    currentBuffer = 0;
                }
                try {
                    if (alSource != 0 && currentSource != alSource) AL10.alDeleteSources(alSource);
                    if (alBuffer != 0 && currentBuffer != alBuffer) AL10.alDeleteBuffers(alBuffer);
                } catch (Exception ignored) {}
            }
        }, "Verity-Music-Playback");
        t.setDaemon(true);
        t.start();
    }

    private static byte[] loadOggBytes(String soundName) {
        String path = "/assets/verity/sounds/" + soundName + ".ogg";
        VerityMod.LOGGER.info("Music: Loading resource via classloader: {}", path);
        try (InputStream is = VerityMusicClient.class.getResourceAsStream(path)) {
            if (is == null) {
                VerityMod.LOGGER.error("Music: class.getResourceAsStream returned null for {}", path);

                // Fallback: try Minecraft resource manager
                try {
                    var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
                    var rl = net.minecraft.resources.ResourceLocation.parse("verity:sounds/" + soundName + ".ogg");
                    var res = rm.getResource(rl);
                    if (res.isPresent()) {
                        VerityMod.LOGGER.info("Music: Found via ResourceManager: {}", rl);
                        return res.get().open().readAllBytes();
                    } else {
                        VerityMod.LOGGER.error("Music: ResourceManager also failed for {}", rl);
                    }
                } catch (Exception e2) {
                    VerityMod.LOGGER.error("Music: ResourceManager fallback error: {}", e2.getMessage());
                }
                return null;
            }
            return is.readAllBytes();
        } catch (Exception e) {
            VerityMod.LOGGER.error("Music: classloader error: {}", e.getMessage());
            return null;
        }
    }

    private static int decodeOggToBuffer(byte[] oggData) {
        ByteBuffer oggBuf = MemoryUtil.memAlloc(oggData.length);
        try {
            oggBuf.put(oggData);
            oggBuf.flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                long handle = STBVorbis.stb_vorbis_open_memory(oggBuf, error, null);
                if (handle == 0) {
                    VerityMod.LOGGER.error("Music: STBVorbis open failed, error={}", error.get(0));
                    return 0;
                }

                try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
                    STBVorbis.stb_vorbis_get_info(handle, info);
                    int channels = info.channels();
                    int sampleRate = info.sample_rate();
                    int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle);

                    VerityMod.LOGGER.info("Music: OGG info — {}ch, {}Hz, {} total samples", channels, sampleRate, totalSamples);

                    int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

                    ShortBuffer pcm = MemoryUtil.memAllocShort(totalSamples * channels);
                    try {
                        int decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
                        int totalShorts = decoded * channels;
                        pcm.clear();
                        pcm.limit(totalShorts);
                        pcm.position(totalShorts);
                        pcm.flip();

                        VerityMod.LOGGER.info("Music: Decoded {} samples ({} short values)", decoded, pcm.remaining());

                        int alBuffer = AL10.alGenBuffers();
                        AL10.alBufferData(alBuffer, format, pcm, sampleRate);

                        int err = AL10.alGetError();
                        if (err != AL10.AL_NO_ERROR) {
                            VerityMod.LOGGER.error("Music: alBufferData error: 0x{}", Integer.toHexString(err));
                        }

                        float durationSec = decoded > 0 ? (float) decoded / sampleRate : 0;
                        VerityMod.LOGGER.info("Music: Buffer ready — ~{}s, format=0x{}", String.format("%.1f", durationSec), Integer.toHexString(format));

                        return alBuffer;
                    } finally {
                        MemoryUtil.memFree(pcm);
                    }
                } finally {
                    STBVorbis.stb_vorbis_close(handle);
                }
            }
        } catch (Exception e) {
            VerityMod.LOGGER.error("Music: decode EXCEPTION — {}", e.toString(), e);
            return 0;
        } finally {
            MemoryUtil.memFree(oggBuf);
        }
    }
}
