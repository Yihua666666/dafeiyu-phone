package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/** Android 本地 TTS：把大肥鱼的观察念出来。 */
public class Speaker implements TextToSpeech.OnInitListener {
    private final Context ctx;
    private TextToSpeech tts;
    private boolean ready;
    private String pending;

    public Speaker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        String engine = null;
        String[] candidates = { "com.google.android.tts" };
        for (int i = 0; i < candidates.length; i++) {
            try {
                this.ctx.getPackageManager().getPackageInfo(candidates[i], 0);
                engine = candidates[i];
                break;
            } catch (Throwable ignored) { }
        }
        Log.i("DafeiyuEye", "TTS engine=" + engine);
        tts = new TextToSpeech(this.ctx, this, engine);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w("DafeiyuEye", "TTS init failed: " + status);
            return;
        }
        try {
            int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.CHINA);
            }
            tts.setSpeechRate(1.0f);
            tts.setPitch(1.03f);
            ready = true;
            if (pending != null) {
                String p = pending;
                pending = null;
                speak(p);
            }
        } catch (Throwable t) {
            Log.w("DafeiyuEye", "TTS setup failed", t);
        }
    }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ctx.getSharedPreferences("eye", Context.MODE_PRIVATE).getBoolean("tts", true)) return;
        String t = shorten(text);
        if (!ready) {
            pending = t;
            return;
        }
        try {
            // 不打断上一句：新的话排到队尾，上一句说完再接。
            tts.speak(t, TextToSpeech.QUEUE_ADD, null, "dafeiyu-" + System.nanoTime());
        } catch (Throwable e) {
            Log.w("DafeiyuEye", "TTS speak failed", e);
        }
    }

    private String shorten(String text) {
        String t = String.valueOf(text == null ? "" : text).replace("\n", " ").trim();
        int max = 50;
        if (t.length() <= max) return t;
        int cut = max;
        for (int i = Math.min(max, t.length() - 1); i > 20; i--) {
            char c = t.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '，' || c == '.' || c == '!' || c == '?') {
                cut = i + 1;
                break;
            }
        }
        return t.substring(0, cut) + "…";
    }

    public void shutdown() {
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Throwable ignored) { }
        tts = null;
        ready = false;
    }
}
