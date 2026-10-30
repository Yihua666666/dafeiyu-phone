package com.deepseek.dafeiyu.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

public class BootService extends Service {
    private static final String TAG = "DafeiyuBoot";
    private static final String CHANNEL = "dafeiyu_boot";
    private static final String CHANNEL_ALERT = "dafeiyu_boot_alert";
    private static final int NOTIFY_ID = 6021;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFY_ID, notification("正在开机启动", "等待系统稳定后启动大肥鱼"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                launchWithRetry();
            }
        }, "dafeiyu-boot-launch").start();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "大肥鱼开机启动", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("开机后启动 AutoX 里的大肥鱼");
            channel.enableLights(false);
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);
            NotificationChannel alert = new NotificationChannel(
                    CHANNEL_ALERT, "大肥鱼启动提醒", NotificationManager.IMPORTANCE_HIGH);
            alert.setDescription("系统拦截后台启动时，点一下手动启动");
            nm.createNotificationChannel(alert);
        }
    }

    private Notification notification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingFlags());

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setColor(Color.parseColor("#2F6BFF"));
        }
        return b.build();
    }

    private int pendingFlags() {
        if (Build.VERSION.SDK_INT >= 23) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }

    private Intent agentIntent() {
        Intent intent = new Intent();
        intent.setClassName("org.autojs.autoxjs.v6",
                "org.autojs.autojs.external.shortcut.ShortcutActivity");
        intent.putExtra("path", "/sdcard/PhoneAgent/agent.js");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    private void launchWithRetry() {
        int[] delays = new int[]{8000, 20000, 40000};
        for (int i = 0; i < delays.length; i++) {
            sleep(delays[i]);
            try {
                if (!Settings.canDrawOverlays(this)) {
                    Log.w(TAG, "overlay permission missing; trying anyway");
                }
                startActivity(agentIntent());
                Log.i(TAG, "agent launch requested, attempt " + (i + 1));
                updateNotification("已发送启动请求", "大肥鱼正在回到手机里");
                sleep(5000);
                stopSelf();
                return;
            } catch (Exception e) {
                Log.e(TAG, "launch failed attempt " + (i + 1), e);
            }
        }
        updateNotification("启动失败", "点开启动器检查悬浮窗/自启动权限");
        showFallback();
        stopSelf();
    }

    /** MIUI 拦截后台启动时，给用户一个一发即中的通知，而不是静默失败。 */
    private void showFallback() {
        try {
            PendingIntent pi = PendingIntent.getActivity(this, 1, agentIntent(),
                    PendingIntent.FLAG_UPDATE_CURRENT | pendingFlags());
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL_ALERT)
                    : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle("大肥鱼还没起来")
                    .setContentText("系统拦住了后台启动，点一下让她回家")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(6022, b.build());
        } catch (Exception e) {
            Log.e(TAG, "fallback notification failed", e);
        }
    }

    private void updateNotification(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFY_ID, notification(title, text));
        } catch (Exception e) {
            Log.e(TAG, "notify failed", e);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
