package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** 只读配置；不保存任何记忆。优先 SharedPreferences，兼容旧的 eye_config.json。 */
public class EyeConfig {
    public String baseUrl = "https://api.deepseek.com";
    public String apiKey = "";
    public String model = "deepseek-flash";
    public int intervalMs = 80;
    public String ttsUrl = "";
    public String ttsKey = "";
    public String ttsVoice = "feiyu";
    public String ttsStyle = "cute";

    public static EyeConfig load(Context ctx) {
        EyeConfig c = new EyeConfig();
        try {
            SharedPreferences p = ctx.getSharedPreferences("eye", Context.MODE_PRIVATE);
            c.baseUrl = p.getString("baseUrl", c.baseUrl);
            c.apiKey = p.getString("apiKey", "");
            c.model = p.getString("model", c.model);
            c.ttsUrl = p.getString("ttsUrl", c.ttsUrl);
            c.ttsKey = p.getString("ttsKey", c.ttsKey);
            c.ttsVoice = p.getString("ttsVoice", c.ttsVoice);
            c.ttsStyle = p.getString("ttsStyle", c.ttsStyle);
        } catch (Throwable ignored) { }
        if (c.apiKey.length() > 8) return c;
        File f = new File("/sdcard/DeepSeekGirl/eye_config.json");
        if (!f.exists()) return c;
        try {
            InputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            JSONObject o = new JSONObject(out.toString("UTF-8"));
            c.baseUrl = o.optString("baseUrl", c.baseUrl).replaceAll("/+$", "");
            c.apiKey = o.optString("apiKey", c.apiKey);
            c.model = o.optString("model", c.model);
            c.intervalMs = o.optInt("intervalMs", c.intervalMs);
            c.ttsUrl = o.optString("ttsUrl", c.ttsUrl).replaceAll("/+$", "");
            c.ttsKey = o.optString("ttsKey", c.ttsKey);
            c.ttsVoice = o.optString("ttsVoice", c.ttsVoice);
            c.ttsStyle = o.optString("ttsStyle", c.ttsStyle);
        } catch (Throwable ignored) { }
        return c;
    }

    public static void save(Context ctx, String baseUrl, String apiKey, String model) {
        save(ctx, baseUrl, apiKey, model, "", "", "feiyu", "cute");
    }

    public static void save(Context ctx, String baseUrl, String apiKey, String model,
                            String ttsUrl, String ttsKey, String ttsVoice, String ttsStyle) {
        SharedPreferences.Editor e = ctx.getSharedPreferences("eye", Context.MODE_PRIVATE).edit();
        e.putString("baseUrl", baseUrl == null ? "" : baseUrl.trim());
        e.putString("apiKey", apiKey == null ? "" : apiKey.trim());
        e.putString("model", model == null ? "deepseek-flash" : model.trim());
        e.putString("ttsUrl", ttsUrl == null ? "" : ttsUrl.trim());
        e.putString("ttsKey", ttsKey == null ? "" : ttsKey.trim());
        e.putString("ttsVoice", ttsVoice == null || ttsVoice.trim().isEmpty() ? "feiyu" : ttsVoice.trim());
        e.putString("ttsStyle", ttsStyle == null || ttsStyle.trim().isEmpty() ? "cute" : ttsStyle.trim());
        e.apply();
    }

    public boolean hasTts() {
        return ttsUrl != null && !ttsUrl.trim().isEmpty();
    }

    public boolean hasVision() {
        return apiKey != null && apiKey.length() > 8;
    }
}
