# dafeiyu-phone 1.0.0

首个可发布版本：把一台闲置 Android 手机变成会自己生活、记得你、能远程聊天的大肥鱼。

## 包含

- `autox/agent.js`：AutoX.js 主 Agent
  - 无障碍观察 + DeepSeek JSON 决策
  - 自主生活（10–20 分钟一轮）
  - 关屏自唤醒、开机自启
  - 长期记忆（facts/self/日记/每日回顾）
  - ntfy 远程聊天、主动找人
  - 文件读写/追加/删除、存储统计
  - 安全边界与任务 watchdog
- `autox/setup_boot.js`：注册 AutoX 开机任务
- `app/DafeiyuChat/`：原生 WebView 聊天 App 源码
- `dist/DafeiyuChat-1.0.0.apk`：可直接安装的 APK
  - 内置 93 张立绘
  - 首次打开需填自己的 ntfy topic
- `scripts/fetch_gallery.py`：下载 93 张立绘
- `scripts/push_to_phone.ps1`：ADB 推送脚本
- `docs/`：安装、架构、安全、排错

## 安装

1. 安装 AutoX.js 6.6.8，开无障碍、悬浮窗、前台服务、自启动
2. 把 `autox/*.js` 放到 `/sdcard/PhoneAgent/`
3. 运行 `agent.js`，填 DeepSeek API Key
4. 安装 APK，点 ⚙ 填入 `/sdcard/DeepSeekGirl/remote/topic.txt` 的 topic
5. 运行 `setup_boot.js` 注册开机自启

详细步骤见 `docs/SETUP.md`。

## 校验

```text
DafeiyuChat-1.0.0.apk
size:   27511283 bytes
sha256: 4A910EC8009C400CD12A6F3F4147AC956A61A61E4BEC42BE233F3D2CBA6A3CB1
```

## 已知限制

- AutoX.js 版本差异可能导致 API 不兼容
- MIUI/HyperOS 的后台与自启动限制会影响稳定性
- 公共 ntfy 服务器可能限流或不可达
- APK 使用 debug 签名
- 立绘为 1600px 分享版；完整 PNG 在原素材仓库

## 安全

请使用专用手机；不要登录主账号。SIM、支付、验证码、安装器仍然硬拦截。
详见 `docs/SAFETY.md`。
