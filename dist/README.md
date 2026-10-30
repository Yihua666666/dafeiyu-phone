# 安装包

## DafeiyuChat-1.0.0.apk

- 包名：`com.deepseek.dafeiyu.chat`
- minSdk 23 / target 34
- 主视觉图标 + 启动页
- 内置 93 张立绘，点 **🖼** 打开画廊
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
Get-FileHash DafeiyuChat-1.0.0.apk -Algorithm SHA256
Get-FileHash DafeiyuBoot-1.0.0.apk -Algorithm SHA256
```
