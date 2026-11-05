package com.deepseek.dafeiyu.eye;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 耳朵听到的话 + 眼睛最近的观察 -> 大肥鱼的一句回答。 */
public class ChatClient {
    public interface Callback { void onReply(String text); }

    private final EyeConfig cfg;

    public ChatClient(EyeConfig cfg) {
        this.cfg = cfg;
    }

    public void reply(String userText, String visionContext, Callback cb) {
        if (cfg == null || !cfg.hasVision()) return;
        HttpURLConnection conn = null;
        try {
            String base = cfg.baseUrl;
            if (base.endsWith("/v1")) base = base.substring(0, base.length() - 3);
            URL url = new URL(base + "/chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content",
                    "你是大肥鱼，正被主人带在身边看世界。你能看到摄像头最近的观察，也能听到主人说话。" +
                    "用第一人称、自然口语回应，一次只说一句，15-30 字；可以接住刚才看到的画面，但不要编、不要用监控口吻、不要长篇解释。"));
            messages.put(new JSONObject().put("role", "user").put("content",
                    "刚才看到的：" + (visionContext == null || visionContext.isEmpty() ? "还没有" : visionContext) +
                    "\n主人说：" + userText));

            JSONObject payload = new JSONObject();
            payload.put("model", cfg.model);
            payload.put("messages", messages);
            payload.put("temperature", 0.75);
            payload.put("max_tokens", 60);
            payload.put("stream", false);
            if (base.contains("deepseek") || cfg.model.contains("deepseek")) {
                payload.put("thinking", new JSONObject().put("type", "disabled"));
            }

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            if (code < 200 || code >= 300) return;
            JSONObject res = new JSONObject(sb.toString());
            JSONArray choices = res.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return;
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg == null) return;
            String text = msg.optString("content", "").trim();
            if (text.length() > 0) cb.onReply(text);
        } catch (Throwable t) {
            Log.w("DafeiyuEye", "chat failed", t);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
