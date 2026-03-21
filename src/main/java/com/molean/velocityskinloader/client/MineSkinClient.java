package com.molean.velocityskinloader.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.molean.velocityskinloader.model.mineskin.SkinInfo;
import com.molean.velocityskinloader.model.mineskin.exception.MineSkinAPIException;
import com.molean.velocityskinloader.model.mineskin.exception.RequestTooSoonException;
import com.molean.velocityskinloader.model.mineskin.exception.SkinGenerateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MineSkinClient extends ApiClient {

    private static final String USER_AGENT = "VelocitySkinLoader/1.0";
    private static final int MAX_RETRIES = 20;
    private static final int RETRY_DELAY_MS = 1000;

    private static MineSkinClient instance = new MineSkinClient();

    public static MineSkinClient instance() {
        return instance;
    }

    private MineSkinClient() {
        super("https://api.mineskin.org");
    }

    @Override
    protected void errorHandle(HttpResponse<?> response) throws MineSkinAPIException {
        switch (response.statusCode()) {
            case 400, 500 -> {
                throw gson.fromJson(((HttpResponse<String>) (response)).body(), SkinGenerateException.class);
            }
            case 429 -> {
                throw gson.fromJson(((HttpResponse<String>) (response)).body(), RequestTooSoonException.class);
            }
            case 200 -> {
            }
            default -> throw new MineSkinAPIException();
        }
    }

    public SkinInfo generateByUpload(@Nullable String variant, @Nullable String name, @Nullable Long visibility, byte @NotNull [] skinImg) throws MineSkinAPIException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(UUID.randomUUID().toString(), ".png");
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(skinImg);
            }
            HashMap<String, Object> data = new HashMap<>();
            if (variant != null) {
                data.put("variant", variant);
            }
            if (name != null) {
                data.put("name", name);
            }
            if (visibility != null) {
                String visibilityStr;
                if (visibility == 0) visibilityStr = "public";
                else if (visibility == 1) visibilityStr = "unlisted";
                else visibilityStr = "private";
                data.put("visibility", visibilityStr);
            }
            data.put("file", tempFile);
            String boundary = new BigInteger(256, new Random()).toString();
            HttpRequest queueRequest = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v2/queue"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", USER_AGENT)
                    .POST(buildMultipartBody(data, boundary))
                    .build();

            HttpResponse<String> queueResponse = httpClient.send(queueRequest, HttpResponse.BodyHandlers.ofString());
            int status = queueResponse.statusCode();
            if (status != 200 && status != 202) {
                if (status == 400 || status == 500) {
                    throw gson.fromJson(queueResponse.body(), SkinGenerateException.class);
                } else if (status == 429) {
                    throw gson.fromJson(queueResponse.body(), RequestTooSoonException.class);
                } else {
                    throw new MineSkinAPIException();
                }
            }
            JsonObject queueJson = JsonParser.parseString(queueResponse.body()).getAsJsonObject();
            String jobId = queueJson.get("id").getAsString();
            SkinInfo result = null;
            for (int i = 0; i < MAX_RETRIES; i++) {
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);

                HttpRequest pollRequest = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/v2/generation/" + jobId))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<String> pollResponse = httpClient.send(pollRequest, HttpResponse.BodyHandlers.ofString());

                if (pollResponse.statusCode() != 200) {
                    continue;
                }

                JsonObject pollJson = JsonParser.parseString(pollResponse.body()).getAsJsonObject();
                String statusStr = pollJson.get("status").getAsString();

                if ("completed".equals(statusStr)) {
                    result = convertToSkinInfo(pollJson);
                    break;
                } else if ("failed".equals(statusStr)) {
                    throw new SkinGenerateException();
                }
            }

            if (result == null) {
                throw new MineSkinAPIException();
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private SkinInfo convertToSkinInfo(JsonObject generation) {
        JsonObject skin = generation.getAsJsonObject("skin");
        JsonObject texture = skin.getAsJsonObject("texture");
        JsonObject data = texture.getAsJsonObject("data");

        JsonObject newRoot = new JsonObject();
        JsonObject dataObj = new JsonObject();
        JsonObject textureObj = new JsonObject();
        textureObj.add("value", data.get("value"));
        textureObj.add("signature", data.get("signature"));
        dataObj.add("texture", textureObj);
        newRoot.add("data", dataObj);

        return gson.fromJson(newRoot, SkinInfo.class);
    }

    private HttpRequest.BodyPublisher buildMultipartBody(Map<String, Object> data, String boundary) throws IOException {
        List<byte[]> byteArrays = new ArrayList<>();
        byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=").getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            byteArrays.add(separator);
            if (entry.getValue() instanceof Path path) {
                String mimeType = Files.probeContentType(path);
                byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + path.getFileName()
                        + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byteArrays.add(Files.readAllBytes(path));
                byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
                byteArrays.add(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
        byteArrays.add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }
}