package net.verity.client.voice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.verity.VerityMod;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class WhisperSTTClient {

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final String STT_PROMPT =
            "\u0412\u0438\u0440\u0438\u0442\u0438, \u0412\u0435\u0440\u0438\u0442\u0438 \u2014 \u044D\u0442\u043E \u0438\u043C\u044F \u043F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430. " +
            "\u0418\u0433\u0440\u043E\u043A \u0440\u0430\u0437\u0433\u043E\u0432\u0430\u0440\u0438\u0432\u0430\u0435\u0442 \u0441 \u0412\u0438\u0440\u0438\u0442\u0438. " +
            "\u0412\u0438\u0440\u0438\u0442\u0438, \u043F\u0440\u0438\u0432\u0435\u0442. \u0412\u0438\u0440\u0438\u0442\u0438, \u043F\u043E\u043C\u043E\u0433\u0438. \u0412\u0438\u0440\u0438\u0442\u0438, \u043A\u0442\u043E \u0442\u044B. " +
            "\u0428\u0430\u0440, \u0436\u0451\u043B\u0442\u044B\u0439 \u0448\u0430\u0440. \u0412\u0435\u0440\u0438\u0442\u0438, \u0438\u0434\u0438 \u0441\u044E\u0434\u0430.";

    public static String transcribe(byte[] wavData, String apiKey, String apiUrl, String model, String language) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No STT API key set. Add stt_api_key to config/verity-client.properties");
        }

        String boundary = "VerityBoundary" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        byte[] body = buildMultipartBody(boundary, wavData, model, language, STT_PROMPT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            VerityMod.LOGGER.warn("STT API returned {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("STT API error: " + response.statusCode());
        }

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json.has("text")) {
            String text = json.get("text").getAsString().trim();
            if (text.isEmpty() || text.equals(".") || text.equals(",")) {
                return null;
            }
            return text;
        }

        return null;
    }

    private static byte[] buildMultipartBody(String boundary, byte[] wavData, String model, String language, String prompt) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String delimiter = "--" + boundary + "\r\n";

        out.write(delimiter.getBytes(StandardCharsets.UTF_8));
        writePartHeader(out, "model");
        out.write(model.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        if (language != null && !language.isEmpty()) {
            out.write(delimiter.getBytes(StandardCharsets.UTF_8));
            writePartHeader(out, "language");
            out.write(language.getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        if (prompt != null && !prompt.isEmpty()) {
            out.write(delimiter.getBytes(StandardCharsets.UTF_8));
            writePartHeader(out, "prompt");
            out.write(prompt.getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        out.write(delimiter.getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(wavData);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return out.toByteArray();
    }

    private static void writePartHeader(ByteArrayOutputStream out, String name) throws java.io.IOException {
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
