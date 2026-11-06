package com.deepseek.dafeiyu.eye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Binder;
import android.view.Surface;
import android.util.Log;

import java.util.Collections;

/** 前台摄像头服务：Camera2 -> 64x36 变化分 -> 选帧 -> vision/。 */
public class EyeService extends Service {
    private static final String TAG = "DafeiyuEye";
    private static final String CH = "dafeiyu_eye";
    private static final int NID = 47;
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private FrameAnalyzer analyzer;
    private Surface previewSurface;
    private OverlayChat overlay;
    private TtsProvider tts;
    private EyeConfig config;
    private ChatClient chatClient;
    private final StringBuilder visionContext = new StringBuilder();
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public EyeService service() { return EyeService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("eye-camera");
        thread.start();
        handler = new Handler(thread.getLooper());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "大肥鱼之眼", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("摄像头视觉观察");
            nm.createNotificationChannel(c);
        }
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("正在带大肥鱼参观世界")
                .setContentText("镜头里的世界只写进 DeepSeekGirl 的观察事件")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            int types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                types |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NID, n, types);
        } else {
            startForeground(NID, n);
        }
        overlay = new OverlayChat(this);
        config = EyeConfig.load(this);
        tts = TtsFactory.create(this, config);
        chatClient = new ChatClient(config);
        analyzer = new FrameAnalyzer(config, desc -> {
            if (overlay != null) overlay.show(desc);
            if (tts != null) tts.speak(desc);
            synchronized (visionContext) {
                visionContext.append(" · ").append(desc);
                if (visionContext.length() > 700) {
                    visionContext.delete(0, visionContext.length() - 700);
                }
            }
        });
        // 等 Activity 的 TextureView 就绪后再开相机，避免 reader-only 会话被立刻重建。
    }

    private void openCamera() {
        if (!MainActivity.visible) {
            Log.i(TAG, "skip camera: Activity not foreground");
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no camera permission");
            return;
        }
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(cid);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    id = cid;
                    break;
                }
            }
            if (id == null && cm.getCameraIdList().length > 0) id = cm.getCameraIdList()[0];
            if (id == null) return;
            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(r -> {
                Image img = null;
                try {
                    img = r.acquireLatestImage();
                    if (img != null && analyzer != null) analyzer.analyze(img);
                } catch (Throwable t) {
                    Log.w(TAG, "frame", t);
                } finally {
                    if (img != null) img.close();
                }
            }, handler);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    camera = c;
                    startPreview();
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camera = null; }
                @Override public void onError(CameraDevice c, int error) {
                    Log.w(TAG, "camera error " + error);
                    c.close();
                    camera = null;
                }
            }, handler);
        } catch (Throwable t) {
            Log.w(TAG, "open camera", t);
        }
    }

    public void setPreviewSurface(Surface s) {
        previewSurface = s;
        if (camera == null) openCamera();
        else startPreview();
    }

    private void startPreview() {
        try {
            if (camera == null) return;
            if (session != null) { try { session.close(); } catch (Throwable ignored) { } session = null; }
            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(reader.getSurface());
            java.util.List<Surface> targets = new java.util.ArrayList<>();
            targets.add(reader.getSurface());
            if (previewSurface != null && previewSurface.isValid()) {
                targets.add(previewSurface);
                b.addTarget(previewSurface);
            }
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            camera.createCaptureSession(targets,
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try { s.setRepeatingRequest(b.build(), null, handler); }
                            catch (Throwable t) { Log.w(TAG, "repeat", t); }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            Log.w(TAG, "session failed");
                        }
                    }, handler);
        } catch (Throwable t) {
            Log.w(TAG, "preview", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    public void speakNow(String text) {
        if (tts != null && text != null && !text.trim().isEmpty()) tts.speak(text);
    }

    public void ask(String text) {
        if (text == null || text.trim().isEmpty() || chatClient == null || !config.hasVision()) return;
        final String heard = text.trim();
        String ctx;
        synchronized (visionContext) { ctx = visionContext.toString(); }
        chatClient.reply(heard, ctx, reply -> {
            if (overlay != null) overlay.show(reply);
            if (tts != null) tts.speak(reply);
        });
    }

    @Override
    public void onDestroy() {
        try { if (session != null) session.close(); } catch (Throwable ignored) { }
        try { if (camera != null) camera.close(); } catch (Throwable ignored) { }
        try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
        if (overlay != null) overlay.destroy();
        if (tts != null) tts.shutdown();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }
}
