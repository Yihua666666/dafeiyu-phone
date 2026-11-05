package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 内置 sherpa-onnx 中文 Paraformer：不依赖系统 ASR，任何安卓都能用。 */
public class AsrEngine {
    public interface Listener {
        void onState(String state);
        void onResult(String text);
        void onError(String error);
    }

    private static final int SR = 16000;
    private final Context ctx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private OfflineRecognizer recognizer;
    private boolean initTried;
    private volatile boolean cancelled;

    public AsrEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void listen(final Listener listener) {
        cancelled = false;
        worker.execute(() -> {
            try {
                ensureInit();
                listener.onState("在听…");
                float[] samples = cancelled ? null : recordUtterance();
                if (samples == null || samples.length < SR / 2) {
                    listener.onError("没听到声音");
                    return;
                }
                listener.onState("识别中…");
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(samples, SR);
                    recognizer.decode(stream);
                    OfflineRecognizerResult r = recognizer.getResult(stream);
                    String text = r == null ? "" : String.valueOf(r.getText()).trim();
                    if (text.isEmpty()) listener.onError("没听清");
                    else listener.onResult(text);
                } finally {
                    try { stream.release(); } catch (Throwable ignored) { }
                }
            } catch (Throwable t) {
                Log.w("DafeiyuEye", "ASR failed", t);
                listener.onError("识别失败");
            }
        });
    }

    private synchronized void ensureInit() throws Exception {
        if (recognizer != null || initTried) return;
        initTried = true;
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(new FeatureConfig(SR, 80, 0f));

        File dir = new File(ctx.getFilesDir(), "asr");
        if (!dir.exists()) dir.mkdirs();
        File modelFile = new File(dir, "model.int8.onnx");
        File tokensFile = new File(dir, "tokens.txt");
        copyRaw(R.raw.asr_model, modelFile);
        copyRaw(R.raw.asr_tokens, tokensFile);

        OfflineParaformerModelConfig para = new OfflineParaformerModelConfig();
        para.setModel(modelFile.getAbsolutePath());

        OfflineModelConfig model = new OfflineModelConfig();
        model.setParaformer(para);
        model.setTokens(tokensFile.getAbsolutePath());
        model.setNumThreads(2);
        model.setProvider("cpu");
        model.setModelType("paraformer");
        config.setModelConfig(model);

        recognizer = new OfflineRecognizer(null, config);
        Log.i("DafeiyuEye", "ASR model loaded");
    }

    private void copyRaw(int resId, File target) throws Exception {
        if (target.exists() && target.length() > 1000) return;
        InputStream in = ctx.getResources().openRawResource(resId);
        FileOutputStream out = new FileOutputStream(target);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private float[] recordUtterance() throws Exception {
        int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(min, SR); // at least 0.5s
        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            throw new IllegalStateException("AudioRecord init failed");
        }
        short[] buf = new short[SR / 10]; // 100ms
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean speech = false;
        long silenceStart = 0;
        long start = System.currentTimeMillis();
        try {
            record.startRecording();
            while (!cancelled && System.currentTimeMillis() - start < 12000) {
                int n = record.read(buf, 0, buf.length);
                if (n <= 0) continue;
                double sum = 0;
                for (int i = 0; i < n; i++) sum += (double) buf[i] * buf[i];
                double rms = Math.sqrt(sum / n);
                if (rms > 420) {
                    speech = true;
                    silenceStart = 0;
                } else if (speech) {
                    if (silenceStart == 0) silenceStart = System.currentTimeMillis();
                    else if (System.currentTimeMillis() - silenceStart > 850) break;
                }
                if (speech) {
                    for (int i = 0; i < n; i++) {
                        out.write(buf[i] & 0xff);
                        out.write((buf[i] >> 8) & 0xff);
                    }
                }
                if (out.size() > SR * 2 * 10) break;
            }
        } finally {
            try { record.stop(); } catch (Throwable ignored) { }
            record.release();
        }
        if (!speech || out.size() < 3200) return null;
        byte[] raw = out.toByteArray();
        float[] samples = new float[raw.length / 2];
        for (int i = 0; i < samples.length; i++) {
            short s = (short) (((raw[i * 2 + 1] & 0xff) << 8) | (raw[i * 2] & 0xff));
            samples[i] = s / 32768.0f;
        }
        return samples;
    }

    public void cancel() {
        cancelled = true;
    }

    public void shutdown() {
        worker.shutdownNow();
        if (recognizer != null) {
            try { recognizer.release(); } catch (Throwable ignored) { }
            recognizer = null;
        }
    }
}
