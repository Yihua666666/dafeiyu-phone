# 第三方 TTS 接口

DafeiyuEye 的语音输出不锁死系统 TTS。仓库内置一个通用 HTTP 适配器，也保留了 Java 接口，下载的人可以接自己的 TTS。

## 两种接法

### 1. 直接填 HTTP 地址（不用改代码）

App 首页“第三方 TTS（可选）”填：

```text
TTS 地址
TTS API Key（可留空）
TTS voice（默认 feiyu）
TTS style（默认 cute）
```

地址留空时自动使用系统 TTS（小米/Google 均可）。

### 2. 自己实现 TtsProvider

接口在：

```text
app/DafeiyuEye/src/com/deepseek/dafeiyu/eye/TtsProvider.java
```

```java
public interface TtsProvider {
    void speak(String text);
    void stop();
    void shutdown();
}
```

约定：

- `speak()` 可以频繁调用，实现方负责排队，不要打断上一句
- `stop()` 用于退到后台/用户主动停止
- `shutdown()` 释放线程和播放器

实现后把 `TtsFactory.create()` 改成返回你的实现即可。

## 内置 HTTP 协议

请求：

```http
POST {TTS 地址}
Authorization: Bearer {TTS API Key}
Content-Type: application/json

{"text":"你好呀","voice":"feiyu","style":"cute"}
```

响应二选一：

### 直接返回音频

```http
HTTP/1.1 200 OK
Content-Type: audio/wav
```

### 返回 JSON

```json
{"audio_url":"https://example.com/audio.wav"}
```

也支持：

```json
{"url":"https://example.com/audio.wav"}
{"audio_base64":"..."}
{"audio":{"url":"..."}}
```

`audio_url` 下载时同样带 Bearer Key。

## 接肥鱼声音服务

`Esmerubphire/astrbot_plugin_feiyu_voice` 的 `/v1/speech` 是**异步 job**：

```json
{"id":"<job_id>","status":"queued"}
```

轮询 `/v1/jobs/{id}`，成功后再取 `/v1/jobs/{id}/audio`。这不符合本适配器“一次返回音频”的同步协议，所以需要二选一：

1. 在服务端加一个很薄的同步代理：提交 job → 轮询完成 → 把 WAV 直接返回；
2. 按 `TtsProvider` 自己写一个 `FeiyuTtsProvider`，内部做 job 轮询。

源码与接口见：

- `server/README.md`
- `server/feiyu_voice_service/app.py`
- `server/feiyu_voice_service/models.py`

## 示例：同步代理

```python
# 伪代码，仅说明协议形状
@app.post("/tts")
def tts(text: str):
    job = post("http://voice:9880/v1/speech",
               json={"voice": "feiyu", "text": text, "style": "cute"},
               headers={"Authorization": "Bearer ..."})
    while True:
        row = get(f"http://voice:9880/v1/jobs/{job['id']}",
                  headers={"Authorization": "Bearer ..."})
        if row["status"] == "success":
            return Response(get_audio(row["id"]), media_type="audio/wav")
        if row["status"] in ("failed", "interrupted"):
            raise HTTPException(502)
        sleep(0.5)
```

## 注意

- 第三方 TTS 会让文本离开手机；当前“前台手动开摄像头/麦克风”的隐私边界只覆盖传感器，不覆盖网络 TTS。
- API Key 存在本机 SharedPreferences，不进日志、不打包。
- 外部服务失败时会记 `HTTP TTS failed`，不会拖垮视觉/语音对话；需要回退时把地址清空即可恢复系统 TTS。
- 肥鱼声库的素材授权与署名见上游项目 `COPYRIGHT.md` / `NOTICE.md`；本项目只提供接口，不打包第三方模型或声音。
