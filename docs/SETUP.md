# 安装与配置

## 0. 准备

- 一台闲置 Android 手机，建议恢复出厂，只登录备用账号
- Wi-Fi
- DeepSeek API Key
- 可接收 ntfy 的设备（手机浏览器 / ntfy App / 本 App）

测试环境：Redmi K60、Android 15、HyperOS、AutoX.js 6.6.8。  
项目不绑定 K60：Android 8+、能运行 AutoX.js 6.6.8 的手机都可以尝试；不同品牌主要差异在自启动、后台运行和悬浮窗权限。

> 不要在这台手机上登录主微信、主支付、主邮箱、唯一 2FA。

## 1. 安装 AutoX.js

AutoX.js 是独立运行时，**不在 `DafeiyuChat` / `DafeiyuBoot` APK 内**。  
从 1.1.0 起，Release 附件提供：

```text
AutoX.js-6.6.8.apk
dafeiyu-phone-1.1.0-bundle.zip
```

<https://github.com/Yihua666666/dafeiyu-phone/releases/tag/v1.1.0>

安装顺序：

```text
AutoX.js 6.6.8 → DafeiyuChat → DafeiyuBoot
```

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
/sdcard/PhoneAgent/boot_on.js
/sdcard/PhoneAgent/boot_off.js
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

### 推荐：DafeiyuBoot 启动器

安装 `dist/DafeiyuBoot-1.0.0.apk`，打开后：

1. 允许悬浮窗
2. 允许通知
3. 电池 → 无限制
4. 系统里允许“大肥鱼启动器”自启动
5. 如果有“后台弹出界面”，也允许
6. 点“测试开机服务”

它会在开机后等系统稳定，再用 `ShortcutActivity` 打开 AutoX：

```text
BOOT_COMPLETED -> BootService -> AutoX ShortcutActivity -> agent.js
```

这样 agent 在正常 UI 上下文运行，面板和远程桥更稳定。

如果系统拦截后台启动，启动器会留一条“大肥鱼还没起来”的通知，点一下即可。

### 备选：AutoX 自己的开机任务

- App 右上角 **开机** → 设置
- 悬浮面板点 `开机自启：开`
- AutoX.js 运行 `/sdcard/PhoneAgent/setup_boot.js`

远程：

```text
/boot on
/boot off
/boot status
```

> 部分 ROM（尤其 HyperOS/MIUI）从 `BOOT_COMPLETED` 直接拉 AutoX 时，脚本会被挂起，面板和远程桥不工作；所以优先用 DafeiyuBoot。

前提：

- 启动器 / AutoX 的自启动权限还在
- 无障碍服务还开着
- 系统没有把应用设为“休眠”

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
