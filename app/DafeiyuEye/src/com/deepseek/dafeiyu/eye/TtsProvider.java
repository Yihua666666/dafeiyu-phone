package com.deepseek.dafeiyu.eye;

/**
 * 语音输出接口。
 *
 * 这是留给第三方 TTS 的稳定插口：实现本接口即可替换系统 TTS，
 * 不需要改 EyeService 或视觉/语音对话逻辑。
 *
 * 约定：
 * - speak() 可以被频繁调用，实现方负责排队，不要打断上一句
 * - stop() 只用于退到后台/用户主动停止
 * - shutdown() 释放线程和播放器
 */
public interface TtsProvider {
    void speak(String text);
    void stop();
    void shutdown();
}
