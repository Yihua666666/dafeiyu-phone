package com.deepseek.dafeiyu.eye;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 64x36 HSV 鍙樺寲鍒?+ 鍒囩偣 + 绉垎 + 闈欓粯鍏滃簳 + 鍘婚噸銆? * 鍙啓 /sdcard/DeepSeekGirl/vision/锛岀粷涓嶅啓 memory/銆? */
public class FrameAnalyzer {
    private static final String TAG = "DafeiyuEye";
    private static final String ROOT = "/sdcard/DeepSeekGirl/vision";
    private static final String EVENTS = ROOT + "/observations.jsonl";
    private static final String FRAMES = ROOT + "/frames";
    private static final int GW = 64;
    private static final int GH = 36;
    private static final float CUT_ABS = 12f;
    private static final float CUT_SPIKE = 2.5f;
    private static final long CUT_GAP_MS = 400;
    private static final float INTEGRAL_NEED = 4f * 12f * 8f;
    private static final float DUP_MIN = 10f;
    private static final long SILENT_FLOOR_MS = 20000;
    private static final long MIN_EMIT_MS = 8000;

    public interface Listener { void onObservation(String desc); }

    private final EyeConfig cfg;
    private final Listener listener;
    private final VisionClient vision;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final float[] prev = new float[GW * GH * 3];
    private final float[] lastKept = new float[GW * GH * 3];
    private final float[] cur = new float[GW * GH * 3];
    private boolean hasPrev = false;
    private boolean hasKept = false;
    private float prevScore = 0;
    private float acc = 0;
    private long lastProcess = 0;
    private long lastCut = 0;
    private long lastKeptAt = 0;
    private long lastEmit = 0;

    public FrameAnalyzer(EyeConfig cfg, Listener listener) {
        this.cfg = cfg;
        this.listener = listener;
        this.vision = new VisionClient(cfg);
        Log.i(TAG, "visionEnabled=" + cfg.hasVision() + " model=" + cfg.model);
        new File(FRAMES).mkdirs();
    }

    public void analyze(Image image) {
        long now = System.currentTimeMillis();
        if (now - lastProcess < Math.max(50, cfg.intervalMs)) return;
        lastProcess = now;
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            sampleHsv(image, w, h, cur);
            if (!hasPrev) {
                System.arraycopy(cur, 0, prev, 0, cur.length);
                hasPrev = true;
                return;
            }
            float score = scorePair(prev, cur);
            System.arraycopy(cur, 0, prev, 0, cur.length);

            String reason = null;
            boolean spike = score >= CUT_ABS && score > CUT_SPIKE * Math.max(1f, prevScore);
            if (spike && now - lastCut >= CUT_GAP_MS && now - lastEmit >= MIN_EMIT_MS) {
                reason = "cut";
                lastCut = now;
                acc = 0;
            } else {
                acc += score;
                if (acc >= INTEGRAL_NEED && now - lastEmit >= MIN_EMIT_MS) {
                    reason = "mark";
                } else if (now - lastEmit >= SILENT_FLOOR_MS) {
                    reason = "floor";
                }
            }
            prevScore = score;
            if (reason == null) return;

            float dup = hasKept ? scorePair(lastKept, cur) : 999f;
            if (hasKept && dup < DUP_MIN && !"cut".equals(reason) && !"floor".equals(reason)) {
                return;
            }
            System.arraycopy(cur, 0, lastKept, 0, cur.length);
            hasKept = true;
            acc = 0;
            lastEmit = now;
            lastKeptAt = now;

            byte[] jpeg = toJpeg(image, w, h);
            if (jpeg == null) return;
            final String path = FRAMES + "/frame_" + now + ".jpg";
            writeFile(path, jpeg);
            final float s = score;
            final float d = dup;
            final String why = reason;
            final JSONObject frameEvent = new JSONObject();
            frameEvent.put("ts", now);
            frameEvent.put("type", "frame");
            frameEvent.put("source", "camera");
            frameEvent.put("reason", why);
            frameEvent.put("score", round(s));
            frameEvent.put("dup", round(d));
            frameEvent.put("image", path);
            appendEvent(frameEvent);

            if (cfg.hasVision()) {
                io.execute(() -> vision.describe(jpeg, desc -> {
                    try {
                        JSONObject obs = new JSONObject();
                        obs.put("ts", System.currentTimeMillis());
                        obs.put("type", "observation");
                        obs.put("source", "camera");
                        obs.put("reason", why);
                        obs.put("desc", desc);
                        appendEvent(obs);
                        if (listener != null) listener.onObservation(desc);
                        new File(path).delete();
                    } catch (Throwable ignored) { }
                }));
            }
        } catch (Throwable t) {
            Log.w(TAG, "analyze failed", t);
        }
    }

    private static double round(float v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private synchronized void appendEvent(JSONObject o) {
        try {
            new File(ROOT).mkdirs();
            FileOutputStream fos = new FileOutputStream(EVENTS, true);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            w.write(o.toString());
            w.write("\n");
            w.close();
        } catch (Throwable ignored) { }
    }

    private static void writeFile(String path, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(data);
        } catch (Throwable ignored) { }
    }

    private static float scorePair(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < b.length; i += 3) {
            float dh = Math.abs(b[i] - a[i]);
            float hueDiff = Math.min(dh, 255f - dh);
            float hueWeight = Math.min(b[i + 1], a[i + 1]) / 255f;
            sum += hueDiff * hueWeight
                    + Math.abs(b[i + 1] - a[i + 1])
                    + Math.abs(b[i + 2] - a[i + 2]);
        }
        return sum / (b.length / 3f);
    }

    private static void sampleHsv(Image image, int w, int h, float[] out) {
        Image.Plane py = image.getPlanes()[0];
        Image.Plane pu = image.getPlanes()[1];
        Image.Plane pv = image.getPlanes()[2];
        ByteBuffer yb = py.getBuffer();
        ByteBuffer ub = pu.getBuffer();
        ByteBuffer vb = pv.getBuffer();
        int yr = py.getRowStride(), yp = py.getPixelStride();
        int ur = pu.getRowStride(), up = pu.getPixelStride();
        int vr = pv.getRowStride(), vp = pv.getPixelStride();
        int idx = 0;
        for (int gy = 0; gy < GH; gy++) {
            int sy = (int) ((gy + 0.5f) * h / GH);
            for (int gx = 0; gx < GW; gx++) {
                int sx = (int) ((gx + 0.5f) * w / GW);
                int yy = yb.get(yr * sy + yp * sx) & 255;
                int uu = ub.get(ur * (sy / 2) + up * (sx / 2)) & 255;
                int vv = vb.get(vr * (sy / 2) + vp * (sx / 2)) & 255;
                float r = clamp(1.164f * (yy - 16) + 1.596f * (vv - 128));
                float g = clamp(1.164f * (yy - 16) - 0.392f * (uu - 128) - 0.813f * (vv - 128));
                float bl = clamp(1.164f * (yy - 16) + 2.017f * (uu - 128));
                rgbToHsv(r, g, bl, out, idx);
                idx += 3;
            }
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static void rgbToHsv(float r, float g, float b, float[] out, int i) {
        float rn = r / 255f, gn = g / 255f, bn = b / 255f;
        float max = Math.max(rn, Math.max(gn, bn));
        float min = Math.min(rn, Math.min(gn, bn));
        float d = max - min;
        float h = 0;
        if (d != 0) {
            if (max == rn) h = ((gn - bn) / d) % 6f;
            else if (max == gn) h = (bn - rn) / d + 2f;
            else h = (rn - gn) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        out[i] = (h / 360f) * 255f;
        out[i + 1] = max == 0 ? 0 : (d / max) * 255f;
        out[i + 2] = max * 255f;
    }

    private static byte[] toJpeg(Image image, int w, int h) {
        try {
            Image.Plane py = image.getPlanes()[0];
            Image.Plane pu = image.getPlanes()[1];
            Image.Plane pv = image.getPlanes()[2];
            byte[] nv21 = new byte[w * h * 3 / 2];
            ByteBuffer yb = py.getBuffer();
            int yr = py.getRowStride(), yp = py.getPixelStride();
            int p = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) nv21[p++] = yb.get(yr * y + yp * x);
            }
            ByteBuffer ub = pu.getBuffer();
            ByteBuffer vb = pv.getBuffer();
            int ur = pu.getRowStride(), up = pu.getPixelStride();
            int vr = pv.getRowStride(), vp = pv.getPixelStride();
            for (int y = 0; y < h / 2; y++) {
                for (int x = 0; x < w / 2; x++) {
                    nv21[p++] = vb.get(vr * y + vp * x);
                    nv21[p++] = ub.get(ur * y + up * x);
                }
            }
            YuvImage yi = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            yi.compressToJpeg(new Rect(0, 0, w, h), 88, bos);
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}

