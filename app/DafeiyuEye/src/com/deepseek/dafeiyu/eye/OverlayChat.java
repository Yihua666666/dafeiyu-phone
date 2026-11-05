package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** 简单的悬浮对话气泡：只显示最新一句观察，8 秒后自动消失。 */
public class OverlayChat {
    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView view;
    private final Runnable hide = () -> {
        try {
            if (view != null && wm != null) wm.removeView(view);
        } catch (Throwable ignored) { }
        view = null;
    };

    public OverlayChat(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void show(String text) {
        if (!Settings.canDrawOverlays(ctx)) return;
        ui.post(() -> {
            try {
                if (wm == null) wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (view == null) {
                    view = new TextView(ctx);
                    view.setTextColor(Color.WHITE);
                    view.setTextSize(15);
                    view.setPadding(28, 20, 28, 20);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.rgb(20, 28, 42));
                    bg.setCornerRadius(28);
                    bg.setStroke(2, Color.rgb(90, 170, 255));
                    view.setBackground(bg);
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP | Gravity.END;
                    lp.x = 24;
                    lp.y = 260;
                    wm.addView(view, lp);
                }
                view.setText("大肥鱼：" + text);
                ui.removeCallbacks(hide);
                ui.postDelayed(hide, 8000);
            } catch (Throwable ignored) { }
        });
    }

    public void destroy() {
        ui.removeCallbacks(hide);
        hide.run();
    }
}
