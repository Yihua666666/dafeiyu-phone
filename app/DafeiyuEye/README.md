# DafeiyuEye · 大肥鱼之眼

独立 Android 相机 App：只负责“看”，不拥有记忆。

```text
Camera2 取景
→ 64×36 HSV 变化检测
→ 切点 / 积分 / 静默兜底 / 去重
→ 选中帧 JPEG
→ 视觉模型生成一句观察
→ 写 /sdcard/DeepSeekGirl/vision/observations.jsonl
→ 删除已识别 JPEG
```

记忆、facts、日记仍然只由 `DeepSeekGirl` 的 agent 写入；本 App 不碰 `memory/`。

## 2.0 能力

- 实时取景
- 变化选帧，不逐帧上传
- 内置 sherpa-onnx 中文离线 ASR
- Android TTS，队列播放，不打断上一句
- 观察精炼成一句事实，由 agent 去重后写入中心记忆
- 前台手动触发：打开 App 不开相机；必须点“带她看看”

## 隐私

- 只有 Activity 可见时才允许 `EyeService.openCamera()`
- `onStop()` 立即停服务、停录音、断相机
- 自主/好奇任务禁止启动本 App 的摄像头/麦克风
- 麦克风只在点“听我说”时启用

## 配置

App 内保存到 SharedPreferences（`eye`），兼容旧文件：

```json
{
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "",
  "model": "deepseek-flash",
  "intervalMs": 80
}
```

API Key 只保存在本机，不写日志、不打包。

## 构建

依赖不走 Git：

- sherpa-onnx Android AAR：`com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.6`
- Kotlin stdlib：`org.jetbrains.kotlin:kotlin-stdlib:1.9.24`
- 中文 Paraformer 模型：`sherpa-onnx-paraformer-zh-small-2024-03-09`

在仓库根目录运行：

```powershell
powershell -File tools/fetch_eye_deps.ps1
powershell -File app/DafeiyuEye/build.ps1
```

输出：

```text
app/DafeiyuEye/dist/DafeiyuEye.apk
```


## 第三方 TTS 接口

系统 TTS 不是唯一选择。App 首页可以填：

```text
TTS 地址
TTS API Key（可留空）
TTS voice
TTS style
```

地址留空 = 系统 TTS；填了就走通用 HTTP 适配器。

协议：

```http
POST {地址}
Authorization: Bearer {Key}
Content-Type: application/json

{"text":"你好呀","voice":"feiyu","style":"cute"}
```

返回音频字节，或 JSON：

```json
{"audio_url":"https://..."}
```

也支持 `url`、`audio_base64`、`audio.url`。

想完全自定义，实现：

```text
src/com/deepseek/dafeiyu/eye/TtsProvider.java
```

接口只有 `speak / stop / shutdown`，由实现方负责排队，不打断上一句。

完整说明见 [docs/TTS.md](../../docs/TTS.md)。

## 输出

```text
/sdcard/DeepSeekGirl/vision/
├── observations.jsonl
└── frames/
```

`observations.jsonl` 每行一条 `observation`，`desc` 是视觉模型给的短句；agent 的 `syncVisionMemory()` 负责精炼、去重、写 `memory/vision.md`。

## 许可

视觉选帧算法移植自 [SAOS-3624/dsh-see-now](https://github.com/SAOS-3624/dsh-see-now)（Apache-2.0），详见 `NOTICE.md`。
