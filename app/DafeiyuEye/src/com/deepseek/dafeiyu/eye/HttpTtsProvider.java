package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用第三方 HTTP TTS 适配器。
 *
 * 请求：
 *   POST {endpoint}
 *   Authorization: Bearer {key}   (key 为空时不发)
 *   Content-Type: application/json
 *   {"text":"...", "voice":"...", "style":"..."}
 *
 * 响应二选一：
 *   1) 直接返回 audio/wav 或 audio/mpeg 字节
 *   2) JSON: {"audio_url":"https://..."} 或 {"url":"..."} 或 {"audio_base64":"..."}
 *
 * 实现方负责串行排队；本类不打断正在播放的上一句。
 * 异步 job 型服务（例如先返回 job_id 再轮询）可在实现方加一层
 * 同步代理，或另写 TtsProvider 实现。
 */
public class HttpTtsProvider implements TtsProvider {
    private final Context ctx;
    private final String endpoint;
    private final String key;
    private final String voice;
    private final String style;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger epoch = new AtomicInteger();
    private volatile MediaPlayer player;
    private volatile boolean stopped;

    public HttpTtsProvider(Context ctx, String endpoint, String key, String voice, String style) {
        this.ctx = ctx.getApplicationContext();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.key = key == null ? "" : key.trim();
        this.voice = voice == null || voice.trim().isEmpty() ? "feiyu" : voice.trim();
        this.style = style == null || style.trim().isEmpty() ? "cute" : style.trim();
    }

    @Override
    public void speak(String text) {
        if (stopped || endpoint.isEmpty() || text == null || text.trim().isEmpty()) return;
        if (!ctx.getSharedPreferences("eye", Context.MODE_PRIVATE).getBoolean("tts", true)) return;
        final String t = text.trim().length() > 500 ? text.trim().substring(0, 500) : text.trim();
        final int myEpoch = epoch.get();
        worker.execute(() -> {
            if (stopped || myEpoch != epoch.get()) return;
            try {
                byte[] audio = request(t);
                if (audio == null || audio.length == 0 || stopped || myEpoch != epoch.get()) return;
                play(audio);
            } catch (Throwable e) {
                Log.w("DafeiyuEye", "HTTP TTS failed", e);
            }
        });
    }

    private byte[] request(String text) throws Exception {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("voice", voice);
            body.put("style", style);

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (!key.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + key);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            String type = String.valueOf(conn.getContentType()).toLowerCase();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) return null;
            byte[] data = readAll(in);
            if (code < 200 || code >= 300) {
                String err = new String(data, "UTF-8");
                if (err.length() > 160) err = err.substring(0, 160);
                Log.w("DafeiyuEye", "HTTP TTS status " + code + " " + err);
                return null;
            }
            if (type.startsWith("audio/")) return data;

            JSONObject res = new JSONObject(new String(data, "UTF-8"));
            String b64 = res.optString("audio_base64", "");
            if (!b64.isEmpty()) return Base64.decode(b64, Base64.DEFAULT);
            String url = res.optString("audio_url", res.optString("url", ""));
            if (url.isEmpty()) {
                JSONObject audio = res.optJSONObject("audio");
                if (audio != null) url = audio.optString("url", "");
            }
            if (url.isEmpty()) return null;
            return download(url);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            if (!key.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + key);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            InputStream in = conn.getInputStream();
            return readAll(in);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    private void play(byte[] data) throws Exception {
        File f = File.createTempFile("feiyu-tts-", ".wav", ctx.getCacheDir());
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
        final CountDownLatch done = new CountDownLatch(1);
        MediaPlayer p = new MediaPlayer();
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        p.setDataSource(f.getAbsolutePath());
        p.setOnCompletionListener(mp -> done.countDown());
        p.setOnErrorListener((mp, what, extra) -> {
            done.countDown();
            return false;
        });
        p.prepare();
        player = p;
        p.start();
        try {
            done.await(120, TimeUnit.SECONDS);
        } finally {
            try { p.stop(); } catch (Throwable ignored) { }
            try { p.release(); } catch (Throwable ignored) { }
            if (player == p) player = null;
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    @Override
    public void stop() {
        epoch.incrementAndGet();
        MediaPlayer p = player;
        if (p != null) {
            try { p.stop(); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void shutdown() {
        stopped = true;
        stop();
        worker.shutdownNow();
    }
}
