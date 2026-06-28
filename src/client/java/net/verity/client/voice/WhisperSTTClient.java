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
            "Вирити, Верити — это имя персонажа. " +
            "Игрок разговаривает с Вирити. " +
            "Вирити, привет. Вирити, помоги. Вирити, кто ты. " +
            "Шар, жёлтый шар. Верити, иди сюда.";

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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

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
