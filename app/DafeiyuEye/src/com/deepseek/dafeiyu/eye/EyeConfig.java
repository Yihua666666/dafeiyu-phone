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

    public static EyeConfig load(Context ctx) {
        EyeConfig c = new EyeConfig();
        try {
            SharedPreferences p = ctx.getSharedPreferences("eye", Context.MODE_PRIVATE);
            c.baseUrl = p.getString("baseUrl", c.baseUrl);
            c.apiKey = p.getString("apiKey", "");
            c.model = p.getString("model", c.model);
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
        } catch (Throwable ignored) { }
        return c;
    }

    public static void save(Context ctx, String baseUrl, String apiKey, String model) {
        SharedPreferences.Editor e = ctx.getSharedPreferences("eye", Context.MODE_PRIVATE).edit();
        e.putString("baseUrl", baseUrl == null ? "" : baseUrl.trim());
        e.putString("apiKey", apiKey == null ? "" : apiKey.trim());
        e.putString("model", model == null ? "deepseek-flash" : model.trim());
        e.apply();
    }

    public boolean hasVision() {
        return apiKey != null && apiKey.length() > 8;
    }
}
