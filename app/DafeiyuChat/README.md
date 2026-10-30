# DafeiyuChat

一个极简 Android WebView 聊天客户端，通过 ntfy 和手机上的 AutoX Agent 通信。

- 包名：`com.deepseek.dafeiyu.chat`
- minSdk 23 / target 34
- 仅申请 `INTERNET`
- 音频/存储权限都不需要
- 93 张立绘内置于 APK，画廊可换头像
- 首启不内置 topic，必须点 ⚙ 填自己的 topic
- 右上角 **开机** → 设置 → 开机自启开关

## 构建

```powershell
# 可选：拉取 93 张立绘到 assets
python ..\..\scripts\fetch_gallery.py

powershell -ExecutionPolicy Bypass -File .\build.ps1
```

产物：

```text
dist\DafeiyuChat.apk
```

构建依赖：JDK 17、Android SDK build-tools 34.0.0、platforms/android-34。
脚本直接调用 `aapt2` / `javac` / `d8` / `zipalign` / `apksigner`，不需要 Gradle。

## 结构

```text
AndroidManifest.xml
assets/index.html     聊天界面
assets/gallery.js     93 张清单
assets/*.jpg          立绘（fetch 后）
src/.../MainActivity.java
src/.../NtfyBridge.java
build.ps1
```
