package org.sparkpixel.velocityskinloader.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.sparkpixel.velocityskinloader.model.mineskin.SkinInfo;
import org.sparkpixel.velocityskinloader.model.mineskin.exception.MineSkinAPIException;
import org.sparkpixel.velocityskinloader.model.mineskin.exception.RequestTooSoonException;
import org.sparkpixel.velocityskinloader.model.mineskin.exception.SkinGenerateException;
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

            // 构建 multipart 数据
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

            // 发送队列请求
            String boundary = new BigInteger(256, new Random()).toString();
            HttpRequest queueRequest = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v2/queue"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", USER_AGENT)
                    .POST(buildMultipartBody(data, boundary))
                    .build();

            HttpResponse<String> queueResponse = httpClient.send(queueRequest, HttpResponse.BodyHandlers.ofString());
            int status = queueResponse.statusCode();
            String body = queueResponse.body();

            // 处理非成功状态码
            if (status < 200 || status >= 300) {
                if (status == 400 || status == 500) {
                    throw gson.fromJson(body, SkinGenerateException.class);
                } else if (status == 429) {
                    throw gson.fromJson(body, RequestTooSoonException.class);
                } else {
                    throw new MineSkinAPIException();
                }
            }

            // 解析队列响应，获取 job.id
            JsonObject queueJson = JsonParser.parseString(body).getAsJsonObject();
            if (!queueJson.has("job") || !queueJson.getAsJsonObject("job").has("id")) {
                throw new MineSkinAPIException();
            }
            String jobId = queueJson.getAsJsonObject("job").get("id").getAsString();

            // 轮询直到完成
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
                    continue; // 可能暂时不可用，继续等待
                }

                JsonObject pollJson = JsonParser.parseString(pollResponse.body()).getAsJsonObject();
                // 获取 job.status
                if (!pollJson.has("job") || !pollJson.getAsJsonObject("job").has("status")) {
                    // 响应格式异常，继续重试
                    continue;
                }
                String statusStr = pollJson.getAsJsonObject("job").get("status").getAsString();

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

    /**
     * 将完成状态的生成响应转换为旧版 SkinInfo 格式。
     * 完成响应结构示例：
     * {
     *   "job": { "status": "completed", ... },
     *   "skin": {
     *     "texture": {
     *       "data": {
     *         "value": "...",
     *         "signature": "..."
     *       }
     *     }
     *   }
     * }
     */
    private SkinInfo convertToSkinInfo(JsonObject generation) {
        // 提取 skin.texture.data
        JsonObject skin = generation.getAsJsonObject("skin");
        if (skin == null || !skin.has("texture")) {
            throw new RuntimeException("Generation response missing skin.texture");
        }
        JsonObject texture = skin.getAsJsonObject("texture");
        if (texture == null || !texture.has("data")) {
            throw new RuntimeException("Generation response missing skin.texture.data");
        }
        JsonObject data = texture.getAsJsonObject("data");
        if (data == null || !data.has("value") || !data.has("signature")) {
            throw new RuntimeException("Generation response missing value or signature");
        }

        // 构建旧版 SkinInfo 所需的 JSON 结构
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