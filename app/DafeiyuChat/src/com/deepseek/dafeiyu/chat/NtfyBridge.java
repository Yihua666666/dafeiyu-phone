package com.deepseek.dafeiyu.chat;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NtfyBridge {
    private final Activity activity;
    private final WebView webView;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private static final String BASE = "https://ntfy.envs.net/";

    public NtfyBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /** Launch AutoX ShortcutActivity to run a small local script. */
    @JavascriptInterface
    public void runScript(String path) {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent();
                intent.setClassName("org.autojs.autoxjs.v6",
                        "org.autojs.autojs.external.shortcut.ShortcutActivity");
                intent.putExtra("path", path);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception ignored) {
            }
        });
    }

    @JavascriptInterface
    public void poll(String topic, String since, String callbackId) {
        pool.execute(() -> {
            Result result;
            try {
                String t = URLEncoder.encode(topic, "UTF-8");
                String s = URLEncoder.encode(since == null || since.isEmpty() ? "10m" : since, "UTF-8");
                result = request("GET", BASE + t + "/json?poll=1&since=" + s, null);
            } catch (Exception e) {
                result = new Result(false, 0, "poll error: " + e);
            }
            deliver(callbackId, result);
        });
    }

    @JavascriptInterface
    public void publish(String topic, String text, String callbackId) {
        pool.execute(() -> {
            Result result;
            try {
                String t = URLEncoder.encode(topic, "UTF-8");
                result = request("POST", BASE + t, text == null ? "" : text);
            } catch (Exception e) {
                result = new Result(false, 0, "publish error: " + e);
            }
            deliver(callbackId, result);
        });
    }

    private void deliver(String callbackId, Result result) {
        String js = "window.__nativeResult(" +
                JSONObject.quote(callbackId) + "," +
                (result.ok ? "true" : "false") + "," +
                result.status + "," +
                JSONObject.quote(result.body) + ")";
        activity.runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private Result request(String method, String urlText, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("User-Agent", "DafeiyuChat/1.0");
            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                conn.setRequestProperty("Title", "Master");
                conn.setRequestProperty("Tags", "phone");
                byte[] data = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String response = stream == null ? "" : readAll(stream);
            return new Result(code >= 200 && code < 300, code, response);
        } catch (Exception e) {
            return new Result(false, 0, String.valueOf(e));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toString("UTF-8");
    }

    private static class Result {
        final boolean ok;
        final int status;
        final String body;

        Result(boolean ok, int status, String body) {
            this.ok = ok;
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }
}
