package com.deepseek.dafeiyu.eye;

import android.content.Context;

/** 选择语音后端：配置了第三方地址就用 HTTP，否则回到系统 TTS。 */
public final class TtsFactory {
    private TtsFactory() { }

    public static TtsProvider create(Context ctx, EyeConfig cfg) {
        if (cfg != null && cfg.ttsUrl != null && !cfg.ttsUrl.trim().isEmpty()) {
            return new HttpTtsProvider(ctx, cfg.ttsUrl, cfg.ttsKey, cfg.ttsVoice, cfg.ttsStyle);
        }
        return new Speaker(ctx);
    }
}
