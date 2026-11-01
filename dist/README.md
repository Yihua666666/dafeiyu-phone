# 安装包

> AutoX.js 6.6.8 是独立运行时，不在 Chat/Boot APK 内。  
> Release 附件提供 `AutoX.js-6.6.8.apk` 和 `dafeiyu-phone-1.1.0-bundle.zip`：  
> <https://github.com/Yihua666666/dafeiyu-phone/releases/tag/v1.1.0>

安装顺序：**AutoX.js 6.6.8 → DafeiyuChat → DafeiyuBoot**

## DafeiyuChat-1.1.0.apk

- 包名：`com.deepseek.dafeiyu.chat`
- versionCode 2 / versionName 1.1.0
- minSdk 23 / target 34
- 主视觉图标 + 启动页
- 内置 93 张立绘，点 **🖼** 打开画廊
- 输入框左侧 **📎** 上传图片（1.1 新增）
- 右上角 **开机** → 设置 → 开机自启开关
- 首次打开点 **⚙**，填入手机上 `/sdcard/DeepSeekGirl/remote/topic.txt` 的 topic
- debug 签名；部分系统会提示“未经安全审核”，选择继续安装

## DafeiyuBoot-1.0.0.apk

- 包名：`com.deepseek.dafeiyu.boot`
- 开机后通过前台服务打开 AutoX 的 `ShortcutActivity`
- 安装后需要：悬浮窗、通知、自启动；部分系统还要“后台弹出界面”
- 被系统拦截后台启动时，会留一条“大肥鱼还没起来”的通知

## 校验

```powershell
Get-FileHash DafeiyuChat-1.1.0.apk -Algorithm SHA256
Get-FileHash DafeiyuBoot-1.0.0.apk -Algorithm SHA256
```

```text
EF84CF03C08B86FA49B4EFA83D4AA107915FC9562B2082690DCD70162D9B781B  DafeiyuChat-1.1.0.apk
a27abc87d8665e823ddfde2bec2917f4600f6f34bd2a4fef278edecef5819fc4  DafeiyuBoot-1.0.0.apk
```
