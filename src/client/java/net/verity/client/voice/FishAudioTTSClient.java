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

        String cleanText = stripMinecraftFormatting(text);
        if (cleanText.isBlank() || cleanText.length() < 2) return;
        if (cleanText.length() > 500) cleanText = cleanText.substring(0, 500);

        final String finalText = cleanText;

        CompletableFuture.supplyAsync(() -> {
            for (String apiKey : keys) {
                try {
                    byte[] result = synthesize(finalText, apiKey);
                    if (result != null) return result;
                } catch (Exception e) {
                    VerityMod.LOGGER.warn("TTS failed with key {}...: {}",
                            apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey, e.getMessage());
                }
            }
            return null;
        }).thenAccept(mp3Data -> {
            if (mp3Data != null && mp3Data.length > 0) {
                Minecraft.getInstance().execute(() -> playMp3(mp3Data, finalText));
            }
        });
    }

    private static byte[] synthesize(String text, String apiKey) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("reference_id", VerityClientConfig.ttsVoiceId());
        body.addProperty("format", "mp3");
        body.addProperty("mp3_bitrate", 128);
        body.addProperty("speed", VerityClientConfig.ttsSpeed());
        body.addProperty("normalize", true);

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
            String errBody = new String(response.body(), StandardCharsets.UTF_8);
            VerityMod.LOGGER.warn("TTS API returned {}: {}", response.statusCode(),
                    errBody.substring(0, Math.min(200, errBody.length())));
            return null;
        }

        return response.body();
    }

    private static void playMp3(byte[] mp3Data, String text) {
        try {
            Files.createDirectories(TTS_CACHE_DIR);
            String fileName = "tts_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".mp3";
            Path mp3File = TTS_CACHE_DIR.resolve(fileName).toAbsolutePath();
            Files.write(mp3File, mp3Data);

            // Оцениваем длительность: 128kbps = ~16KB/сек
            int estimatedDurationSec = Math.max(2, mp3Data.length / 16000);

            VerityMod.LOGGER.info("TTS: Playing {} ({} bytes, ~{}s) for: {}",
                    fileName, mp3Data.length, estimatedDurationSec,
                    text.substring(0, Math.min(50, text.length())));

            // Запускаем воспроизведение в отдельном потоке
            Thread playbackThread = new Thread(() -> {
                Process proc = null;
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        proc = Runtime.getRuntime().exec(new String[]{"powershell", "-nop", "-c",
                                "Add-Type -AssemblyName presentationCore; " +
                                "$p = New-Object System.Windows.Media.MediaPlayer; " +
                                "$p.Open([uri]'" + mp3File.toString().replace("'", "''") + "'); " +
                                "Start-Sleep -m 200; $p.Play(); " +
                                "Start-Sleep -s " + (estimatedDurationSec + 2) + "; $p.Close()"});
                    } else if (os.contains("mac")) {
                        proc = Runtime.getRuntime().exec(new String[]{"afplay", mp3File.toString()});
                    } else {
                        proc = Runtime.getRuntime().exec(new String[]{"mpg123", mp3File.toString()});
                    }

                    // Ждём пока процесс жив → аудио играет
                    while (proc.isAlive()) {
                        Thread.sleep(50);
                    }
                } catch (Exception e) {
                    VerityMod.LOGGER.error("TTS playback thread error: {}", e.getMessage());
                } finally {
                    // Аудио закончилось — закрываем рот
                    net.verity.client.render.VerityItemRenderer.setTalking(false);
                    // Удаляем временный файл
                    try { Files.deleteIfExists(mp3File); } catch (Exception ignored) {}
                }
            }, "Verity-TTS-Playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

        } catch (Exception e) {
            VerityMod.LOGGER.error("TTS playback failed: {}", e.getMessage());
        }
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
