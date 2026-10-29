# 架构

```text
┌────────────────────────────── K60 ──────────────────────────────┐
│                                                                  │
│  AutoX.js (无障碍 + 悬浮窗)                                      │
│    ├─ agent.js                                                   │
│    │   ├─ observe() 读屏幕节点 / OCR                              │
│    │   ├─ plan() DeepSeek JSON 决策                              │
│    │   ├─ executeAction() tap/swipe/text/open_app/file...        │
│    │   ├─ selfThink() 每 10–20 分钟自主活动                       │
│    │   ├─ memory/ 事实、自述、日记、每日回顾                       │
│    │   └─ remote bridge 轮询 ntfy                                 │
│    └─ setup_boot.js 注册 BOOT_COMPLETED 任务                     │
│                                                                  │
│  DafeiyuChat APK (WebView)                                       │
│    └─ Java bridge → ntfy publish/poll                            │
│                                                                  │
└───────────────┬──────────────────────────────────────────────────┘
                │ HTTPS
         ┌──────▼──────┐
         │ ntfy.envs.net│  topic（也是密码）
         └──────▲──────┘
                │ HTTPS
      ┌─────────┴─────────┐
      │ 你的手机 / 浏览器   │
      └───────────────────┘
```

## Agent 主循环

1. 用户任务或自主思考产生 `task`
2. `observe()` 取当前包名、Activity、无障碍节点；节点太少时 OCR
3. 策略层检查：安全中心、支付、短信、电话、SIM、安装器、敏感控件
4. DeepSeek 返回一个 JSON 动作
5. `executeAction()` 执行；每一步写日志、写内存归档
6. `finish()` 写日记、排下一轮、必要时回远程

## 记忆

- `memory/chat/YYYY-MM-DD.jsonl`：原文归档
- `memory/facts.md`：长期事实
- `memory/self.md`：自述
- `diary/YYYY-MM-DD.md`：日记
- `memory/summaries/`：每日回顾
- 回复前 `recallMemory()` 用关键词 + 最近天数检索，不依赖向量库

## 远程桥

- Agent 每 15 秒 `poll=1&since=<lastId>`
- 收到消息后按意图路由：聊天 / 做事 / `/stop`
- 回复以 `Title: Dafeiyu` 发回 topic
- App 每 15 秒轮询，切后台暂停；429 退避 60 秒

## 为什么不用 Gradle

APK 只是 WebView + 一个 Java bridge，构建脚本直接使用 Android SDK 的
`aapt2`、`javac`、`d8`、`zipalign`、`apksigner`，不依赖 Gradle 和网络依赖。
