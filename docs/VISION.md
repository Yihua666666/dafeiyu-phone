# 看图能力

大肥鱼现在有两种“看”：

| 场景 | 方式 |
|---|---|
| **你上传的图片** | 视觉模型，真正描述画面 |
| 当前屏幕 | 本地 PaddleOCR，只读文字 |
| 相册里的旧照片 | 本地 PaddleOCR，只读文字 |
| 网络图片 | 本地 PaddleOCR，只读文字 |

**视觉模型只在你上传图片时使用，其他情况一律 OCR。** 这是硬规则，不是靠提示词自觉。

## 你上传的图片放哪

```text
/sdcard/DeepSeekGirl/inbox/
```

两种上传方式：

1. 在 ntfy App / 浏览器里给 topic 附一张图  
   消息里的附件会被 agent 下载到 `inbox/`，然后自动进入看图任务。
2. 直接把图片放进 `/sdcard/DeepSeekGirl/inbox/`  
   然后说“看看我上传的这张图”。

只有 `inbox/` 里的图片会走视觉模型。相册、屏幕、网络图都不会。

## 视觉链路

```text
inbox/ 里的用户图片
  → AutoX 本地读取、缩放
  → 视觉模型（OpenAI 兼容 /chat/completions）
  → 文字描述
  → 大肥鱼继续行动
```

DeepSeek 的 **`deepseek-flash`** 本身支持图片输入（JPEG / PNG / GIF / WebP），所以主模型是 `deepseek-flash` 时，直接复用同一个 Key/Base 就能看图，不需要额外视觉模型。

如果主模型是 `deepseek-chat`，或者想换 OpenRouter、Qwen-VL 等服务，再单独填 Vision Model。

## 配置

在 AutoX 悬浮面板点 **设置**，依次填：

```text
API Key / Base URL / Model       主模型，负责聊天和决策
Vision Model                    视觉模型，留空=自动（DeepSeek 用 deepseek-flash）
Vision Base                     留空=复用主 Base URL
Vision Key                      留空=复用主 Key
```

主 Base 是 `api.deepseek.com` 时，留空会自动用 `deepseek-flash` 看图；不会拿 `deepseek-chat` 硬塞图片。

## 可用的视觉服务

任何兼容 OpenAI `chat/completions` + `image_url` 的服务都可以。常见配置：

### DeepSeek

```text
主 Model:      deepseek-flash
Vision Model:  留空即可
Vision Base:   留空
Vision Key:    留空
```

### OpenRouter

```text
Vision Base:  https://openrouter.ai/api/v1
Vision Model: qwen/qwen2.5-vl-72b-instruct
             或 google/gemini-2.0-flash-001
Vision Key:   你的 OpenRouter Key
```

### 硅基流动 SiliconFlow

```text
Vision Base:  https://api.siliconflow.cn/v1
Vision Model: Qwen/Qwen2.5-VL-32B-Instruct
Vision Key:   你的 SiliconFlow Key
```

### OpenAI

```text
Vision Base:  https://api.openai.com/v1
Vision Model: gpt-4o-mini
Vision Key:   你的 OpenAI Key
```

### 本地 Ollama（PC 常开）

```text
Vision Base:  http://<电脑局域网 IP>:11434/v1
Vision Model: qwen2.5vl:7b
Vision Key:   ollama（随便填，Ollama 不校验）
```

这种方式需要 PC 和手机在同一 Wi-Fi，且 PC 常开；和“完全脱离 PC”冲突，只适合调试。

## 她怎么用

上传图片后说：

```text
看看我上传的这张图
这张图里是什么
```

只有这时会走视觉模型。其他情况：

```text
看看当前屏幕          → 本地 OCR
看看最近的相册照片    → 本地 OCR
看看这个网页图片      → 本地 OCR
```

对应动作：

```json
{"action":"look","path":"/sdcard/DeepSeekGirl/inbox/upload.jpg","question":"你看到了什么"}
{"action":"look_screen","question":"屏幕上有什么字"}
{"action":"look_latest","question":"最近照片里有什么字"}
```

## 隐私边界

- 只有 `inbox/` 里你上传的图片会发给视觉服务。
- 屏幕、相册旧照片、网络图片只在本机做 OCR，不上传原图。
- 截屏可能包含通知、聊天、账号信息；她不应该看支付、银行、验证码、身份证和别人的隐私聊天。
- 视觉/OCR 返回的文字会进入模型上下文；图片本身不写进记忆目录。
- 本地图片只允许 `/sdcard/` 下，不允许 `/sdcard/Android/`。
- 网络图片只允许 `http/https`。
- 没有 Wi-Fi 时不会发图。

## 截屏权限

`look_screen` 需要 MediaProjection 截屏权限。Android 14/15 第一次会弹出“分享或录制应用”，需要手动选择：

- **整个屏幕**：能看所有应用
- **单个应用**：只能看选中的应用

AutoX 重启后，系统可能再次询问；如果卡在权限弹窗，先手动点一次“开始”。只想看相册里的照片时，用 `look` / `look_latest`，不需要截屏权限。

## 限制

- 没有配置视觉模型时，`look` 会失败，不会退回文本模型。
- 部分“视觉模型”只支持图片 URL，不支持 data URL；这种服务需要先把图片上传到可访问地址，当前实现不处理。
- 图片会缩放到最长边 1280、JPEG 80，细节特别小的图可能看不清。
- 视觉描述可能出错；她应该以“我看到”开头，不把猜测写成事实。
