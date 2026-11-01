# 熄屏 / 亮屏边界（README 已补）

> 状态：已实现，已合并进根目录 `README.md`。  
> 记录时间：2026-11-01  
> 适用范围：Redmi K60 / Android 15 / HyperOS / AutoX.js v6.6.8

## 一句话

大肥鱼**不需要界面的操作可以全程熄屏**；  
**需要点屏幕的操作必须短暂亮屏**，做完自动熄屏。  
个人微信无法纯手机自动回复；QQ 可以。

## 1. 熄屏也能做的

这些不依赖当前界面：

- 远程聊天 / ntfy 收发
- Fast Chat 生成回复
- 记忆、日记、文件读写
- 通知监听
- 定时、作息、自主思考
- QQ 消息解析和回复生成

手机锁屏时照常工作。

## 2. 必须短暂亮屏的

这些需要 Activity 处于可交互状态：

- 打开 App
- 点击、滑动
- 读当前屏幕 / OCR
- QQ 自动输入和发送
- 刷视频、看番、搜索

Android 熄屏后界面会挂起，无障碍也点不到。流程只能是：

```text
熄屏
→ 来任务
→ wakeUp 亮屏
→ 操作
→ home()
→ KEYCODE_SLEEP 重新熄屏
```

## 3. 代码实现位置

### 主 agent

`autox/agent.js`

- `wakeScreenForTask()`：屏幕关着时点亮；处理无密码锁屏的上滑；成功点亮后把 `taskWokeScreen` 记为 `true`
- `sleepAfterTask()`：任务 `finish()` 时，如果 `taskWokeScreen` 为真，就 `KEYCODE_SLEEP`
- `runTask()`：只在任务真正要跑、网络和 API 检查通过后调用 `wakeScreenForTask()`
- 自主思考的预检查**不再提前点亮**：她只是想一想、不操作界面时不会白亮一下
- 用户正在用手机（屏幕本来亮着）时，`taskWokeScreen` 为假，任务结束不会强制锁屏

日志关键字：

```text
屏幕关着，本鱼自己点亮
任务结束，已重新熄屏
```

### QQ watcher

`autox/im_watch.js`

- 记录通知来之前是否熄屏
- 熄屏则 `device.wakeUp()`
- 回复后 `leaveApp()`：`back()` → `home()`
- 如果原来是熄屏，再 `KEYCODE_SLEEP`
- 这样下一条 QQ 消息还会正常弹通知

## 4. 真正全程不亮屏的通道

只有不经过界面的通道：

- PC 微信 bridge
- QQ 官方机器人
- 企业微信 / 公众号

个人微信在手机上做不到：

- 通知没有 `RemoteInput`
- 无障碍树为空（`uiautomator` 只有根节点）
- MIUI 拒绝后台拉起 `ScreenCaptureRequestActivity`

## 5. 可选策略（以后可加）

- 只在充电时允许 UI 任务，电池时不亮屏
- 电池时自主活动只写记忆/日记，不操作 App
- 给 `sleepAfterTask()` 加配置开关，例如 `SLEEP_AFTER_UI_TASK = true`
- 记录每次亮屏时长，估算耗电

## 6. README 插入建议

建议放到：

- `## 她能做什么` 后面补一句“熄屏能做什么、什么时候会亮屏”
- `## 已知限制` 里补微信/QQ 的区别
- 新增 `## 熄屏与后台运行` 小节，直接引用本文
