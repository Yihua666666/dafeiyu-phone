# 安装与配置

## 0. 准备

- 一台闲置 Android 手机，建议恢复出厂，只登录备用账号
- Wi-Fi
- DeepSeek API Key
- 可接收 ntfy 的设备（手机浏览器 / ntfy App / 本 App）

测试环境：Redmi K60、Android 15、HyperOS、AutoX.js 6.6.8。

> 不要在这台手机上登录主微信、主支付、主邮箱、唯一 2FA。

## 1. 安装 AutoX.js

安装 AutoX.js 6.6.8 后，逐项打开：

1. AutoX 首页左上角菜单 → **无障碍服务**
2. 同菜单 → **悬浮窗**
3. 同菜单 → **前台服务**
4. 系统设置 → 应用 → AutoX → 电池 → **无限制**
5. MIUI/HyperOS：安全中心 → 自启动管理 → 允许 **Autox.js v6**
6. Android 13+ 如果无障碍开关是灰的：
   应用信息 → 右上角 ⋮ → 允许受限制的设置

如果 AutoX 的无障碍服务被系统关掉，Agent 仍然可能启动，但读不到屏幕。

## 2. 放入脚本

目标路径：

```text
/sdcard/PhoneAgent/agent.js
/sdcard/PhoneAgent/chat.js
/sdcard/PhoneAgent/setup_boot.js
```

可以用文件管理器复制，也可以用 ADB：

```powershell
adb push autox/agent.js /sdcard/PhoneAgent/agent.js
adb push autox/chat.js /sdcard/PhoneAgent/chat.js
adb push autox/setup_boot.js /sdcard/PhoneAgent/setup_boot.js
```

或者：

```powershell
powershell -File scripts/push_to_phone.ps1 -Serial <serial>
```

## 3. 第一次运行

AutoX.js → 文件 → `/sdcard/PhoneAgent/agent.js` → 运行。

第一次会弹出配置框：

```text
API Key    sk-...
Base URL   https://api.deepseek.com/v1
Model      deepseek-flash
```

配置存在 AutoX 的 `storages` 里，不写进脚本。

运行成功后，左上角会出现悬浮面板，状态一般是 **在线**。

如果面板不见了：

- 点桌面上的 **大肥鱼** App，顶部也能看在线状态
- 或在 AutoX → 管理里停掉旧脚本，重新运行 `agent.js`

## 4. 远程聊天

Agent 第一次启动会生成：

```text
/sdcard/DeepSeekGirl/remote/topic.txt
```

里面是类似：

```text
dafeiyu-xxxxxxxxxxxxxxxxxxxxxxxx
```

安装 `dist/DafeiyuChat-1.0.0.apk`，打开 App：

1. 点右上角 **⚙**
2. 填入上面那串 topic
3. 发送消息

手机端也可以通过浏览器访问：

```text
https://ntfy.envs.net/<topic>
```

topic 就是密码，不要公开。

## 5. 开机自启

三种开法：

- App 右上角 **开机** → 设置 → 开机自启
- 悬浮面板点 `开机自启：开`
- AutoX.js 运行一次 `/sdcard/PhoneAgent/setup_boot.js`

也可以远程：

```text
/boot on
/boot off
/boot status
```

它们都会写入 AutoX 自己的任务库：

```text
/data/data/org.autojs.autoxjs.v6/databases/IntentTaskDatabase.db
```

记录：

```text
BOOT_COMPLETED -> /sdcard/PhoneAgent/agent.js
```

重启后 AutoX 的 `StaticBroadcastReceiver` 会自动拉起 Agent。

> MIUI 从 BOOT_COMPLETED 拉起 AutoX 时，悬浮面板偶尔不显示；脚本其实已经在跑。打开“大肥鱼”App 或 AutoX 的“管理”就能确认/恢复面板。

前提：

- AutoX 自启动权限还在
- 无障碍服务还开着
- AutoX 更新/清数据后需要重跑 `setup_boot.js`

## 6. 验证

```text
1. App 里发“hello” → 她回
2. 关屏 → 等一个自主思考周期 → 屏幕自己亮，日志出现“屏幕关着，本鱼自己点亮”
3. 重启手机 → 不点 AutoX，日志出现“大肥鱼醒了”
4. 发 /stop → 当前任务停止
```

## 7. 更新

- 只更新脚本：覆盖 `/sdcard/PhoneAgent/agent.js`，在 AutoX 里停止旧脚本再运行
- 更新 App：`adb install -r dist/DafeiyuChat-1.0.0.apk`（同签名可覆盖）
- 换 API Key / 模型：悬浮面板 → 设置

## 8. 自定义

- 人设：`/sdcard/DeepSeekGirl/persona.md`
- 远程服务器：`autox/agent.js` 的 `NTFY_BASE` 和 `NtfyBridge.java` 的 `BASE`
- 立绘：`app/DafeiyuChat/assets/` 和 `/sdcard/DeepSeekGirl/assets/whale-girl/`
- 作息：`AWAKE_START` / `AWAKE_END`
- 自主思考间隔：`AUTO_MIN_MINUTES` / `AUTO_MAX_MINUTES`
