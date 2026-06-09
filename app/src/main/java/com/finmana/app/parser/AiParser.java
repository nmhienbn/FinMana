package com.finmana.app.parser;

import com.finmana.app.data.AppSettings;
import com.finmana.app.data.ParsedBalance;
import com.finmana.app.data.SettingsStore;
import com.finmana.app.data.TransactionType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiParser {
    private static final MediaType JSON_MT = MediaType.get("application/json");
    private final SettingsStore settingsStore;
    private final OkHttpClient client = new OkHttpClient();

    public AiParser(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public AiParseResult parse(String packageName, String appName, String notification) {
        AppSettings settings = settingsStore.get();
        if (settings.apiUrl.isEmpty() || settings.apiKey.isEmpty() || settings.aiModel.isEmpty()) {
            return null;
        }
        String prompt = "Phân tích thông báo ví/ngân hàng sau. Chỉ trả JSON thuần:\n" +
            "{\"isBalance\":true,\"amount\":150000,\"type\":\"INCOME|EXPENSE\",\"regex\":\"regex có group 1 là số tiền\"}\n" +
            "Regex phải khái quát, Java-compatible, không chứa thông tin cá nhân. Nếu không phải biến động số dư: {\"isBalance\":false}.\n" +
            "Package: " + packageName + "\n" +
            "App: " + appName + "\n" +
            "Thông báo: " + notification;

        try {
            JSONObject body = new JSONObject()
                .put("model", settings.aiModel)
                .put("temperature", 0)
                .put("messages", new JSONArray()
                    .put(new JSONObject()
                        .put("role", "user")
                        .put("content", prompt)));
            Request request = new Request.Builder()
                .url(settings.apiUrl)
                .header("Authorization", "Bearer " + settings.apiKey)
                .post(RequestBody.create(body.toString(), JSON_MT))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("AI HTTP " + response.code());
                String respBody = response.body() != null ? response.body().string() : "";
                JSONObject envelope = new JSONObject(respBody);
                String content = envelope.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content");
                int start = content.indexOf('{');
                int end = content.lastIndexOf('}');
                if (start < 0 || end < 0) return null;
                String jsonText = content.substring(start, end + 1);
                JSONObject json = new JSONObject(jsonText);
                if (!json.optBoolean("isBalance", false)) return null;
                long amount = json.optLong("amount", 0);
                if (amount <= 0) return null;
                TransactionType type;
                try {
                    type = TransactionType.valueOf(json.getString("type").toUpperCase());
                } catch (Exception e) {
                    return null;
                }
                String regex = json.optString("regex", "");
                return new AiParseResult(
                    new ParsedBalance(amount, type, appName, "ai"),
                    regex.isEmpty() ? null : regex
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> fetchModels(String apiUrl, String apiKey) {
        String modelsUrl = deriveModelsUrl(apiUrl);
        Request request = new Request.Builder()
            .url(modelsUrl)
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            String body = response.body() != null ? response.body().string() : "";
            JSONObject json = new JSONObject(body);
            List<String> models = new ArrayList<>();
            if (json.has("data")) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String id = item.optString("id", "");
                    if (!id.isEmpty()) models.add(id);
                }
            }
            return models;
        } catch (Exception e) {
            return null;
        }
    }

    private String deriveModelsUrl(String chatUrl) {
        if (chatUrl.endsWith("/chat/completions")) {
            return chatUrl.substring(0, chatUrl.length() - "/chat/completions".length()) + "/models";
        }
        if (chatUrl.endsWith("/chat/completions/")) {
            return chatUrl.substring(0, chatUrl.length() - "/chat/completions/".length()) + "/models";
        }
        int idx = chatUrl.lastIndexOf("/v1/");
        if (idx >= 0) {
            return chatUrl.substring(0, idx + 3) + "/models";
        }
        return chatUrl.replaceAll("/+$", "") + "/models";
    }
}
