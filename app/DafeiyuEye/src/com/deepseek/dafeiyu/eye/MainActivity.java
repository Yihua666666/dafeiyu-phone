package com.deepseek.dafeiyu.eye;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private TextView status;
    private TextureView preview;
    private EditText baseUrl;
    private EditText apiKey;
    private EditText model;
    private EyeService service;
    private boolean bound;
    private Surface surface;
    private Speaker speaker;
    private VoiceChat voice;
    private boolean fallbackSpeechTried;
    private AsrEngine asr;
    public static volatile boolean visible;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((EyeService.LocalBinder) binder).service();
            bound = true;
            if (surface != null) service.setPreviewSurface(surface);
            status.setText("眼睛已连接：正在带她看世界");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(12, 18, 28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("大肥鱼之眼");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("带大肥鱼参观你的世界 · 变化才看 · 不拥有记忆");
        sub.setTextColor(Color.rgb(170, 190, 210));
        sub.setTextSize(13);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub);

        preview = new TextureView(this);
        preview.setSurfaceTextureListener(this);
        LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        pv.bottomMargin = dp(12);
        root.addView(preview, pv);

        status = new TextView(this);
        status.setText("打开摄像头，带她看看");
        status.setTextColor(Color.rgb(120, 210, 255));
        status.setTextSize(14);
        status.setPadding(0, 0, 0, dp(10));
        root.addView(status);

        baseUrl = field(root, "Base URL", "https://api.deepseek.com", false);
        apiKey = field(root, "API Key", "", true);
        model = field(root, "模型", "deepseek-flash", false);

        Button save = new Button(this);
        save.setText("保存 API 配置");
        save.setOnClickListener(v -> {
            EyeConfig.save(this, baseUrl.getText().toString(), apiKey.getText().toString(),
                    model.getText().toString());
            status.setText("已保存。重新打开镜头后生效。");
        });
        root.addView(save);

        Button start = new Button(this);
        start.setText("带她看看");
        start.setOnClickListener(v -> ensurePermissionsAndStart());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("退出眼睛");
        stop.setOnClickListener(v -> {
            if (bound) {
                try { unbindService(conn); } catch (Throwable ignored) { }
                bound = false;
                service = null;
            }
            Intent i = new Intent(this, EyeService.class);
            i.setAction("stop");
            stopService(i);
            status.setText("已退出");
            finish();
        });
        root.addView(stop);

        CheckBox tts = new CheckBox(this);
        tts.setText("语音播报她看到的");
        tts.setTextColor(Color.WHITE);
        tts.setChecked(getSharedPreferences("eye", MODE_PRIVATE).getBoolean("tts", true));
        tts.setOnCheckedChangeListener((b, checked) ->
                getSharedPreferences("eye", MODE_PRIVATE).edit().putBoolean("tts", checked).apply());
        root.addView(tts);

        Button talk = new Button(this);
        talk.setText("听我说");
        talk.setOnClickListener(v -> ensureMicAndTalk());
        root.addView(talk);

        Button listen = new Button(this);
        listen.setText("试听大肥鱼声音");
        listen.setOnClickListener(v -> {
            if (speaker != null) speaker.speak("你好，我是大肥鱼，正在参观你的世界。");
            status.setText("正在试听语音…");
        });
        root.addView(listen);

        Button overlay = new Button(this);
        overlay.setText("悬浮窗权限");
        overlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        root.addView(overlay);

        TextView hint = new TextView(this);
        hint.setText("她看到的：\n/sdcard/DeepSeekGirl/vision/observations.jsonl\n\n悬浮窗只显示最新一句，8 秒后消失。");
        hint.setTextColor(Color.rgb(150, 165, 180));
        hint.setTextSize(12);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint);

        setContentView(scroll);
        speaker = new Speaker(this);
        asr = new AsrEngine(this);
        voice = new VoiceChat(this, new VoiceChat.Listener() {
            @Override public void onText(String text) {
                status.setText("听到了：" + text);
                if (service != null) service.ask(text);
                else status.setText("眼睛还没连接");
            }
            @Override public void onState(String state) {
                status.setText(state);
            }
            @Override public void onFailure(int code) {
                if (!fallbackSpeechTried) {
                    fallbackSpeechTried = true;
                    startSystemSpeech();
                }
            }
        });
        loadConfig();
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        // 不自动开相机：必须由用户点“带她看看”明确发起。
        // 大肥鱼自己好奇、思考、打开这个页面时都不会碰摄像头和麦克风。
    }

    @Override
    protected void onStop() {
        visible = false;
        stopAllSensors();
        super.onStop();
    }

    private void stopAllSensors() {
        try { if (voice != null) voice.stop(); } catch (Throwable ignored) { }
        try { if (asr != null) asr.cancel(); } catch (Throwable ignored) { }
        if (bound) {
            try { unbindService(conn); } catch (Throwable ignored) { }
            bound = false;
            service = null;
        }
        try { stopService(new Intent(this, EyeService.class)); } catch (Throwable ignored) { }
    }

    private void loadConfig() {
        EyeConfig c = EyeConfig.load(this);
        baseUrl.setText(c.baseUrl);
        apiKey.setText(c.apiKey);
        model.setText(c.model);
    }

    private EditText field(LinearLayout root, String hint, String value, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(120, 140, 160));
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(e);
        return e;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void ensureMicAndTalk() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 12);
            return;
        }
        if (asr == null) return;
        asr.listen(new AsrEngine.Listener() {
            @Override public void onState(String state) { status.setText(state); }
            @Override public void onResult(String text) {
                status.setText("听到了：" + text);
                if (service != null) service.ask(text);
            }
            @Override public void onError(String error) { status.setText(error); }
        });
    }

    private void startSystemSpeech() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "说给大肥鱼听");
            startActivityForResult(i, 21);
        } catch (Throwable t) {
            status.setText("手机没有可用的语音识别");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 21 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null && !list.isEmpty()) {
                String text = list.get(0);
                status.setText("听到了：" + text);
                if (service != null) service.ask(text);
            }
        }
    }

    private void ensurePermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 11);
            return;
        }
        startEye();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 11 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startEye();
        } else if (requestCode == 12 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (voice != null) voice.toggle();
        } else {
            status.setText("权限未授予");
        }
    }

    private void startEye() {
        if (!visible || bound) return;
        Intent i = new Intent(this, EyeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
        status.setText("正在打开世界…");
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        if (bound && service != null) service.setPreviewSurface(surface);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (service != null) service.setPreviewSurface(null);
        if (surface != null) surface.release();
        surface = null;
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    @Override
    protected void onDestroy() {
        if (bound) { try { unbindService(conn); } catch (Throwable ignored) { } }
        if (speaker != null) speaker.shutdown();
        if (voice != null) voice.destroy();
        if (asr != null) asr.shutdown();
        super.onDestroy();
    }
}
