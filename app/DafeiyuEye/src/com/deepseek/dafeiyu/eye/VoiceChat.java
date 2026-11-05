package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/** 手机麦克风 -> 系统语音识别 -> 文本。 */
public class VoiceChat {
    public interface Listener {
        void onText(String text);
        void onState(String state);
        void onFailure(int code);
    }

    private final Context ctx;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private boolean listening;

    public VoiceChat(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    listener.onState("在听…");
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { listening = false; }
                @Override public void onError(int error) {
                    listening = false;
                    listener.onState("没听清（" + error + "）");
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) listener.onText(list.get(0));
                }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
        } else {
            Log.w("DafeiyuEye", "no recognition service");
        }
    }

    public boolean available() {
        return recognizer != null;
    }

    public void toggle() {
        if (recognizer == null) {
            listener.onState("手机没有语音识别引擎");
            return;
        }
        if (listening) {
            stop();
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toString());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        listener.onState("在听…");
        try {
            recognizer.startListening(i);
        } catch (Throwable t) {
            listening = false;
            listener.onState("麦克风打不开");
        }
    }

    public void stop() {
        listening = false;
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Throwable ignored) { }
        }
    }

    public void destroy() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Throwable ignored) { }
            recognizer = null;
        }
    }
}
