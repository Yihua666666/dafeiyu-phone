# DafeiyuBoot

独立开机启动器：不依赖 AutoX 的 `BOOT_COMPLETED` 脚本上下文，开机后用正常的
`ShortcutActivity` 启动 `agent.js`。

```text
BOOT_COMPLETED
  → BootReceiver
  → BootService（前台服务）
  → 等待 8 / 20 / 40 秒重试
  → ShortcutActivity 打开 /sdcard/PhoneAgent/agent.js
```

- 包名：`com.deepseek.dafeiyu.boot`
- minSdk 23 / target 34
- 需要：悬浮窗、通知、自启动；部分系统还需要“后台弹出界面”
- 被系统拦截后台启动时，会留一条“大肥鱼还没起来”的通知，点一下手动启动

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

产物：

```text
dist\DafeiyuBoot.apk
```

构建依赖：JDK 17、Android SDK build-tools 34.0.0、platforms/android-34。
不需要 Gradle。

## 安装后

打开 App，依次点：

1. 允许悬浮窗 / 后台弹出界面
2. 允许通知
3. 电池 → 无限制
4. MIUI / ColorOS / OriginOS 等系统里允许“自启动”
5. 点“测试开机服务”

重启手机验证。

> 不同系统对后台启动的限制不同；如果系统仍然拦截，使用通知回退，或把 AutoX
> 放在前台/最近任务里。
