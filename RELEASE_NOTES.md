# dafeiyu-phone 2.0.0

> 相对 1.1.0：从“会聊天、会刷手机”，升级到“能看、能听、能说”，并把摄像头/麦克风的边界收紧到前台手动触发。

发布日期：2026-11-05

## 一句话

2.0 的大肥鱼有了自己的眼睛、耳朵和嘴，但只有在 DafeiyuEye 前台、你亲手点按钮时，才允许使用摄像头和麦克风。

## 新增

### 1. DafeiyuEye：眼睛

- 独立 Android App：`app/DafeiyuEye`
- Camera2 + TextureView 实时取景
- 64×36 HSV 网格变化检测：切点 / 积分 / 静默兜底 / 去重
- 只写 `/sdcard/DeepSeekGirl/vision/observations.jsonl`，不写 `memory/`
- 观察由 agent 精炼、去重后写入 `memory/vision.md` 和 `memory/chat`
- APK 内置中文离线 ASR 模型，首次启动复制到 App 私有目录

### 2. 耳朵：sherpa-onnx 离线中文 ASR

- 内置 `sherpa-onnx` + Paraformer int8 中文模型
- 不依赖 `SpeechRecognizer`、不依赖小米/Google ASR
- 16kHz 录音 + 静音检测，一次说一句
- 结果直接进 `ChatClient`，和视觉观察一起作为上下文

### 3. 嘴：不抢话的 TTS

- `QUEUE_FLUSH` → `QUEUE_ADD`
- 上一句说完才接下一句
- 单句最多 50 字，视觉描述 20 字、语音回复 15–30 字
- 系统 TTS（小米/Google 均可）

### 4. 隐私：前台手动触发

- 打开 DafeiyuEye 不会自动开摄像头
- 必须点“带她看看”才启动 EyeService
- 必须点“听我说”才启动录音
- 退到桌面/锁屏/切到别的 App：`onStop` 立即停服务、停录音、断相机
- 自主/好奇任务禁止启动 DafeiyuEye 的摄像头或麦克风
- AutoX 本身 CAMERA / RECORD_AUDIO 均为 denied

### 5. 任务模型

- Ticket / 队列 / 单 Executor / Report
- `/sdcard/PhoneAgent/tickets.json` 持久化，重启恢复
- 卡死自动复位 + 30 秒重试门限
- 自主、远程、面板任务统一排队

### 6. QQ / ntfy 记忆

- QQ 特别关心强制按私聊处理，不再误判成群聊
- QQ 上下文最近 6 小时、最多 12 条
- QQ 和 ntfy 的用户消息、助手回复都写入 `memory/chat/YYYY-MM-DD.jsonl`
- QQ 主界面未读轮询、滚动到底部、读最新一条

## 已知限制

- ASR 模型约 82MB，APK 约 94MB；首次使用需复制模型
- sherpa-onnx 依赖只保留 arm64-v8a
- 视觉记忆每 30 秒最多处理 12 条，历史积压需要几轮消化
- TTS 队列暂无限长，观察特别密集时可能积压

## 致谢

视觉变化选帧参考 [SAOS-3624/dsh-see-now](https://github.com/SAOS-3624/dsh-see-now)（Apache-2.0）。DafeiyuEye 为 Android Camera2 原生重写。

## 验证

- `adb install` 94MB APK 成功
- 打开 App 不点按钮：EyeService = 0
- 点“带她看看”：EyeService 启动
- 按 Home：EyeService = 0，相机 DISCONNECT
- ASR：`DafeiyuEye: ASR model loaded`
- TTS：`onSynthesizeText Start/End`
- 视觉记忆：`视觉记忆 +12 条`

﻿# dafeiyu-phone 1.1.0

> 相对 1.0.0：从“能自己生活、能远程聊天”，升级到“能接住上下文、能刷 B站、能收梗、能熄屏做事、能自己调面板”。

发布日期：2026-11-01

## 一句话对比

| | 1.0.0 | 1.1.0 |
|---|---|---|
| 定位 | 手机里的自主 Agent + ntfy 聊天 | 加上 IM 自动回复、B站、梗库、短时记忆和屏幕边界 |
| 聊天记忆 | 最近 4 条；图片另走任务 | 最近 8 条；图片带上下文；快捷回复也入历史 |
| QQ | 只有通用 bridge 文档 | `im_watch.js` 通知监听 + 无障碍发送 |
| B站 | 只能让通用 Agent 临时点 | 专门 B站模式：真实播放、看懂、日记、收藏 |
| 梗库 | 无 | B站/QQ 文字梗；B站评论图和封面 |
| 熄屏 | 会亮屏，但不保证熄回去 | UI 任务结束自动熄屏；QQ 回完回桌面/熄屏 |
| 悬浮窗 | 固定大小/位置 | 可调大小/位置，布局持久化 |
| 记忆 | facts 无上限、无去重 | 注入预算 + 事实归档 + 去重合并；facts 仍不限条数 |
| 聊天 App | 只能发文字 | 增加 📎 上传图片 |
| 文档 | 安装/架构/安全 | 增加聊天、看图、熄屏、B站、收藏、记忆导入 |

## 新增

### 1. 短时记忆与图片上下文

- 聊天上下文从 4 条提升到 8 条
- “在吗/哈哈/晚安”等 0 token 快捷回复也会写入历史
- 用户上传图片不再被当成独立“看图任务”
- 图片处理会读取最近对话，并把上一轮她的话带进视觉提问
- 如果她刚说“想看你的糖”，用户发糖的图，她会接住，不会问“为什么突然发图”
- 图片事件以 `[图片]` 写回历史，后续文字还能继续接

### 2. QQ 自动回复

新增 `autox/im_watch.js`：

- 监听 QQ 通知
- 私聊、@ 都回
- 群聊没 @：15% 概率、10 分钟冷却后随机接一句
- 无障碍找到 QQ 输入框和 `send_btn`
- 发完 `back()` → `home()`，需要时重新熄屏
- 最近 8 条 QQ 对话进入上下文
- 支持 QQ 文字梗收集

限制：

- 只适合专用 QQ 小号
- 只拿得到通知里的文字，拿不到 QQ 图片/表情包
- 需要通知使用权 + 无障碍 + QQ 已登录
- 个人号自动化有平台风控风险

### 3. B站模式

远程发：

```text
刷b站
刷b站 10分钟
```

能力：

- 热门榜；登录 Cookie 可用时走个性化推荐
- `bilibili://video/BV...` 深链打开真实视频
- 播放 4–110 秒，按视频时长和随机兴趣决定
- 18% 概率点进去几秒就退
- 每次间隔 1.5–6.5 秒
- 历史 BV 去重
- 读取标题、UP、简介、字幕片段、热门评论
- 清洗广告/促销后，用 Fast Chat 生成 1–2 句“看懂”
- 写进日记
- 不点赞、不评论、不投币、不购买

可选登录：

```text
B站登录
```

打开二维码，用另一台设备的 B站 App 扫码；没扫码时自动回退热门榜。

### 4. 梗库 / 收藏

目录：

```text
/sdcard/DeepSeekGirl/collections/
  memes.jsonl
  images/
```

B站会收集：

- 她自己的吐槽
- 热门评论梗
- 视频封面
- 评论区图片（最多 2 张）

QQ 会收集：

- 含“笑死/哈哈/梗/离谱/绷不住/破防/乐子/草/典/急了/表情包”的文字

手动：

```text
收藏 这个梗的内容
```

限制：QQ 图片/表情包拿不到；B站图片只存本地，不自动发布。

### 5. 熄屏与悬浮面板

熄屏：

- 纯聊天、记忆、日记、ntfy：熄屏也能跑
- 需要界面的任务：短暂亮屏，结束自动熄屏
- QQ 回复：亮屏几秒，发完回桌面并熄屏
- 不会在“只是想一想”时白亮屏

悬浮面板：

- 点 **调整** 输入 `宽x高`、`左边距,上边距`
- 默认 `1000×820`
- 尺寸/位置持久化，收起再展开、重启都保持
- 支持系统调整手柄/拖动

### 6. 记忆系统加固

- 注入预算：总量 3600 字符
  - `self` 1000
  - `facts` 1400
  - 相关旧事 8 条
  - 最近日记 8 行 / 700 字符
- facts 不再只取文件末尾，而是按相关度/时间/权重选
- facts **不设条数上限**
- 重复/同义事实自动合并
- 旧事实归档到 `facts_archive.md`，不删除
- 支持 `[core]` / `[protected]` 优先保留
- 启动时和写入后都会跑一次去重
- 日记写入失败不再静默：会写日志并尝试“读旧内容 + 重写”

### 7. 聊天 App

- 输入框左侧增加 **📎** 上传图片
- 上传走 ntfy attachment，Agent 下载到 `inbox/` 后视觉理解
- APK 版本号升级到 `1.1.0`

### 8. 稳定性与文档

- LLM 空返回/超时/5xx 自动重试 3 次
- 规划调用关闭 thinking，避免 max_tokens 被思维链吃光
- 新增文档：
  - `docs/FAST_CHAT.md`
  - `docs/VISION.md`
  - `docs/SCREEN_OFF.md`
  - `docs/BILIBILI.md`
  - `docs/COLLECTIONS.md`
  - `docs/MEMORY_IMPORT.md`

## 从 1.0.0 升级

1. 备份手机上的 `/sdcard/DeepSeekGirl/`
2. 用 1.1.0 的 `autox/agent.js` 覆盖旧文件
3. 需要 QQ 自动回复时，再放入 `autox/im_watch.js`
4. 安装 `dist/DafeiyuChat-1.1.0.apk`
5. QQ 需要：通知使用权、无障碍、QQ 已登录
6. B站可选：远程发 `B站登录` 扫码
7. 记忆文件兼容，不需要迁移；旧的重复 facts 会在启动时自动归档

## 安装包

AutoX.js 6.6.8 是独立运行时，**不在 Chat/Boot APK 内**。1.1.0 Release 额外附：

```text
AutoX.js-6.6.8.apk
dafeiyu-phone-1.1.0-bundle.zip   # AutoX + Chat + Boot + 安装顺序
```

安装顺序：

```text
AutoX.js 6.6.8 → DafeiyuChat → DafeiyuBoot
```

AutoX 为原样附带的第三方 APK，MPL 2.0 + 非商业限制，源码：
<https://github.com/aiselp/AutoX>

## 包校验

```text
DafeiyuChat-1.1.0.apk
size:   29923958
sha256: EF84CF03C08B86FA49B4EFA83D4AA107915FC9562B2082690DCD70162D9B781B

DafeiyuBoot-1.0.0.apk
size:   2524020
sha256: a27abc87d8665e823ddfde2bec2917f4600f6f34bd2a4fef278edecef5819fc4

AutoX.js-6.6.8.apk
size:   76544027
sha256: DEC81B4A8739F173C084626676C862E008F9D840EAC61346AC0CC3F3DCB8096E

dafeiyu-phone-1.1.0-bundle.zip
size:   102667410
sha256: AB9192963119BC23CD35FBE3FF6E8489464EB0456708141CC6F801E6ABDE9BB2
```

## 已知限制

- 个人微信**不支持**纯手机自动回复：
  - 通知没有 `RemoteInput`
  - 无障碍树为空
  - MIUI 拒绝后台拉起截屏授权
  - 只能走 PC bridge 或官方通道
- QQ 自动回复只适合专用小号，且只支持文字
- B站 Cookie 扫码是可选实验功能；未登录时是热门榜
- B站只播放、理解、记录，不互动
- 记忆同步（手机 ↔ PC dsh-world）**没有包含在 1.1**
- 公共 ntfy 可能限流
- APK 使用 debug 签名
- 立绘是 1600px 分享版，完整 PNG 不在 APK 内

## 1.0.0 回顾

1.0.0 是首个可发布版本：

- 完全跑在手机里的 AutoX Agent
- 无障碍观察 + DeepSeek JSON 决策
- 自主生活、关屏自唤醒、开机自启
- 本地记忆（facts/self/日记/每日回顾）
- ntfy 远程聊天、主动找人
- 93 张立绘、原生聊天 App、独立开机启动器
- 安全边界与任务 watchdog

1.0.0 的 tag 和 release 仍然保留，可回退。

