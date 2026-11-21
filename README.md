# HyperClicker 连点器 · Auto Clicker

[English](#english) · [中文](#chinese)

> 一个免 root 手机连点器（小米 HyperOS 1.0.26 / Android 8.0+），支持高频点击与低频点击（每 15 分钟 / 30 分钟 / 1 小时一次）。
> A root-free auto clicker (Xiaomi HyperOS 1.0.26 / Android 8.0+) with both high-frequency and low-frequency tapping (every 15 min / 30 min / 1 hour).

---

<a id="chinese"></a>
# 中文

## 简介

一个面向 **小米 HyperOS 1.0.26**（也兼容其它 Android 8.0+ 机型）的免 root 手机连点器。核心思路：用系统自带的 **无障碍服务（AccessibilityService）** 的 `dispatchGesture()` 注入点击手势，因此**无需 root、无需电脑 ADB**。

## 功能

- **点击点位**：切到目标界面后，用悬浮球「录点」在屏幕上点选任意位置（坐标归一化，跨分辨率/横竖屏一致）。
- **长按点位**：录点时**长按屏幕**即可录一个长按点位（按住多久就按多久），也可在编辑页手动设置每个点位的长按时长。
- **点位细粒度编辑**：每个点位可单独设置**长按时长(ms)**、**点击后间隔(ms)**，并可**上下调整循环顺序**、删除单个点位。
- **方案保存**：把名称、点击点位、频率、执行方式保存为多套配置方案，随时切换、编辑、删除。
- **频率粒度**（与市面连点器不同）：
  - **高频点击**：毫秒/秒级，例如每 10ms、100ms、1s、5s 点一次。
  - **低频点击**：分钟/小时级，例如**每 15 分钟、30 分钟、1 小时点一次**。
- **执行方式**：无限循环 / 固定次数 / 固定时长。
- **悬浮控制窗**：录点、开始、停止、测试、隐藏，支持拖动。其中「测试」会点击一次屏幕中心，用于快速验证手势注入是否生效。
- **开机自启（低频）**：重启后自动恢复已启用的低频任务。
- **后台保活（低频）**：低频任务运行期间会启动前台保活服务（显示「连点器运行中」常驻通知），避免被 HyperOS 省电策略杀掉导致漏点。

## 技术方案（为什么这样做）

| 需求 | 实现 |
| --- | --- |
| 点击注入（免 root） | `AccessibilityService.dispatchGesture()` |
| 高频循环 | 协程循环（`Dispatchers.Main` + `delay`） |
| 低频循环（15min/30min/1h） | `AlarmManager.setExactAndAllowWhileIdle()` 精确闹钟 + `BootReceiver` 开机恢复（**不能用协程 delay**，进程会被 HyperOS 杀死/Doze 冻结） |
| 后台保活 | 前台服务 `KeepAliveService`（`specialUse` 类型） |
| 坐标跨机型 | 归一化坐标 0~1，执行时按当前屏幕尺寸换算 |
| 方案持久化 | Room（SQLite） |

技术栈：Kotlin + Jetpack Compose（主界面）+ View（悬浮窗/录点层），Room + KSP。
关键版本：AGP 8.5.2、Kotlin 1.9.24、compileSdk 34 / minSdk 26 / targetSdk 34、Gradle 8.7。

## 构建

本机需要 **JDK 17** 与 **Android Studio（含 Android SDK 34）**。

> 若本机默认 `java` 不是 JDK 17，可在 `gradle.properties` 里加一行
> `org.gradle.java.home=<JDK17 路径>`（注意：这是机器相关配置，勿提交到仓库），
> 或把 `JAVA_HOME` 指向 JDK 17。

### 方式一：Android Studio（推荐）

1. 用 Android Studio 打开本项目根目录（`HyperClicker`）。
2. 首次打开会提示同步 Gradle，等待下载完成。
3. 连接手机（开启 USB 调试）后点 **Run**，或 `Build → Build APK(s)` 生成 APK。
4. APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 方式二：命令行

```bash
cd HyperClicker
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

> 仓库已包含完整的 Gradle Wrapper（`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar`），
> 命令行可直接构建，无需预装 Gradle。若 `services.gradle.org` 下载慢，可把
> `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成国内镜像，例如
> `https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip`。

## 手机端设置（HyperOS 1.0.26 必做）

HyperOS 杀后台很激进，低频点击要稳定，请**全部**完成以下设置：

1. **开启无障碍服务**
   设置 → 更多设置 → 无障碍 → 已下载的应用 →「连点器」→ 开启。
   （应用首页也会给出「去开启」入口）

2. **悬浮窗权限**
   设置 → 应用设置 → 授权管理 → 显示悬浮窗（或「显示在其他应用上层」）→ 连点器 → 允许。

3. **精确闹钟权限**（低频点击必需，Android 12+）
   设置 → 应用设置 → 授权管理 → 闹钟和提醒 → 连点器 → 允许。

4. **通知权限**（保活通知需要，Android 13+）
   设置 → 应用设置 → 授权管理 → 通知 → 连点器 → 允许。

5. **自启动**
   设置 → 应用设置 → 授权管理 → 自启动 → 连点器 → 允许。
   （应用内「打开自启动设置」会尝试直达 MIUI 自启动页，失败则退回应用详情）

6. **省电策略设为「无限制」**
   设置 → 省电与电池 → 应用智能省电 → 连点器 → 无限制。

> 建议顺便在「最近任务」里把连点器**锁定**（下滑任务卡片加锁），避免被一键清理杀掉。

## 使用流程

1. 打开连点器，首页点右下角 **+** 新建方案。
2. 填写方案名，选择「高频点击」或「低频点击」，设置间隔（可用快捷预设，低频自带 **15 分钟 / 30 分钟 / 1 小时**）。
3. 选择执行方式（无限 / 次数 / 时长），需要开机自启的低频任务打开「开机自启」开关，点保存。
4. 回到列表，点方案卡片上的 **选择**（把该方案设为录点目标）。
5. 切到目标界面，点悬浮球 **录点** → 屏幕上**点选=普通点**、**长按=长按点** → 底部 **完成**。
6. 回到 App 编辑方案，可**微调每个点位的长按 / 间隔 / 顺序**；回列表点 **开始** 开始连点。
7. 悬浮球可 **开始 / 停止 / 隐藏**，支持拖动。

## 低频执行说明

- **息屏会中断点击**：`dispatchGesture` 注入的点击需要屏幕亮着才能送达目标 App；息屏后输入管线关闭，点击不会生效。低频长时间挂机时请保持屏幕常亮（悬浮窗显示期间已自动保持屏幕常亮；也可用「开发者选项 → 充电时保持唤醒」或调长屏幕超时），并让目标 App 保持在前台。
- **间隔语义**：每个点位后的「间隔(ms)」是「点完这个点后，等待多久再点下一个点」。**最后一个点的间隔会被忽略**（后面没有下一个点了）。「每 X 分钟/小时」的计时从**最后一个点点击完成后**开始计算。

## 目录结构

```
app/src/main/java/com/hyperclicker/
├── ClickerApp.kt                 # Application，数据库/仓库入口
├── data/                         # 数据层：Room 实体、DAO、仓库
│   ├── ClickScheme.kt            #   方案实体 + 点位(归一化坐标)序列化
│   ├── SchemeDao.kt
│   ├── AppDatabase.kt
│   └── SchemeRepository.kt
├── service/                      # 核心无障碍服务
│   ├── ClickAccessibilityService.kt  # 手势注入、高频/低频、悬浮球、录点
│   ├── KeepAliveService.kt       #   前台保活服务（低频任务运行期间防杀）
│   └── ClickerState.kt           #   跨组件运行状态
├── scheduler/                    # 低频调度
│   ├── AlarmScheduler.kt         #   精确闹钟
│   ├── AlarmReceiver.kt          #   触发接收器（含有界重试）
│   └── BootReceiver.kt           #   开机自启恢复
├── ui/                           # Compose 界面
│   ├── MainActivity.kt
│   ├── SchemeListScreen.kt
│   ├── SchemeEditScreen.kt
│   ├── MainViewModel.kt
│   ├── PointPicker.kt            #   全屏录点层
│   └── theme/
└── util/PermissionUtils.kt       # 各权限跳转
```

## 无法点击？排查清单（HyperOS）

如果「开始」后没有任何点击效果，请按顺序排查：

1. **先点悬浮球「测试」**：它会点击一次屏幕中心。
   - 若目标 App 中央的按钮被点到了 → 手势注入正常，问题出在**点位坐标**，请重新录点并确认录点界面与执行界面一致（分辨率/横竖屏）。
   - 若「测试」也没反应 → 手势注入被系统拦截，继续下面步骤。
2. **重新开关一次无障碍服务**：更新安装后系统有时会自动停用无障碍服务，必须到 设置 → 更多设置 → 无障碍 →「连点器」**先关闭再重新开启**，否则手势会被 HyperOS 静默丢弃。
3. **确认悬浮球能正常显示**：悬浮球出现说明服务已连接；若悬浮球不出现，说明无障碍服务或悬浮窗权限没开全。
4. **省电策略设为「无限制」+ 允许自启动 + 通知权限**（见上文「手机端设置」第 4、5、6 步），并在最近任务里锁定本应用。
5. **用 ADB 看日志**（可选）：手机连电脑执行 `adb logcat -s HyperClicker`，开始连点后若出现 `dispatchGesture 返回 false` 或 `dispatchGesture 异常`，说明系统明确拒绝了手势注入。
6. 部分应用（银行、支付、系统安全中心等）会拦截外部手势注入，换到普通 App（如浏览器、便签）验证是否点击。

## 说明与限制

- 本应用仅注入点击手势，**不收集、不上传任何屏幕内容**。无障碍配置里 `canRetrieveWindowContent=true` 只是为了规避 HyperOS 对手势注入的限制（主流连点器均如此声明）。
- 无障碍服务被系统或用户关闭后，低频任务会停止；重新开启服务并点「开始」即可恢复。
- 部分应用（银行、支付等）可能有防自动化检测，连点是否生效取决于目标应用。
- 精确闹钟在 Android 13/14 上默认关闭，务必按上文第 3 步授权，否则低频点击不准时。

---

<a id="english"></a>
# English

## Overview

A root-free auto clicker for **Xiaomi HyperOS 1.0.26** (also compatible with other Android 8.0+ devices). It injects tap gestures through the system **AccessibilityService**'s `dispatchGesture()`, so **no root and no ADB are required**.

## Features

- **Tap points**: switch to the target screen, then use the floating ball's "Record" to tap any position (coordinates are normalized, consistent across resolutions and orientations).
- **Long-press points**: hold the screen while recording to create a long-press point (press duration = hold duration); you can also set the press duration manually in the edit page.
- **Fine-grained point editing**: each point has an individual **press duration (ms)** and **gap after tap (ms)**, and you can **reorder** or delete individual points.
- **Saved schemes**: save name, tap points, frequency, and repeat mode into multiple profiles; switch, edit, or delete them anytime.
- **Frequency granularity** (different from most auto clickers):
  - **High-frequency**: millisecond/second level, e.g. every 10ms, 100ms, 1s, 5s.
  - **Low-frequency**: minute/hour level, e.g. **every 15 minutes, 30 minutes, 1 hour**.
- **Repeat modes**: infinite loop / fixed count / fixed duration.
- **Floating control window**: record, start, stop, test, hide — draggable. "Test" taps the screen center once to quickly verify gesture injection works.
- **Auto-start on boot (low-frequency)**: restored enabled low-frequency tasks are re-scheduled after reboot.
- **Keep-alive (low-frequency)**: while a low-frequency task runs, a foreground keep-alive service (persistent "Auto clicker running" notification) is started to prevent HyperOS from killing the process and missing taps.

## How it works (why these choices)

| Need | Implementation |
| --- | --- |
| Tap injection (no root) | `AccessibilityService.dispatchGesture()` |
| High-frequency loop | Coroutine loop (`Dispatchers.Main` + `delay`) |
| Low-frequency loop (15min/30min/1h) | `AlarmManager.setExactAndAllowWhileIdle()` exact alarm + `BootReceiver` boot restore (**must not use coroutine `delay`** — the process would be killed/frozen by HyperOS Doze) |
| Background keep-alive | Foreground service `KeepAliveService` (`specialUse` type) |
| Cross-device coordinates | Normalized 0~1, converted to current screen size at execution time |
| Scheme persistence | Room (SQLite) |

Stack: Kotlin + Jetpack Compose (main UI) + View (floating window / point-recording overlay), Room + KSP.
Key versions: AGP 8.5.2, Kotlin 1.9.24, compileSdk 34 / minSdk 26 / targetSdk 34, Gradle 8.7.

## Build

You need **JDK 17** and **Android Studio (with Android SDK 34)**.

> If your default `java` is not JDK 17, either add
> `org.gradle.java.home=<path-to-jdk17>` to `gradle.properties`
> (note: this is machine-specific and should not be committed), or point `JAVA_HOME` at JDK 17.

### Option 1: Android Studio (recommended)

1. Open the project root (`HyperClicker`) in Android Studio.
2. On first open it will prompt to sync Gradle; wait for the download to finish.
3. Connect a phone (USB debugging on) and click **Run**, or `Build → Build APK(s)`.
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

### Option 2: Command line

```bash
cd HyperClicker
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

> The repo ships the complete Gradle Wrapper (`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar`),
> so you can build from the command line without installing Gradle. If `services.gradle.org` is slow,
> change `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to a mirror, e.g.
> `https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip`.

## Device setup (required on HyperOS 1.0.26)

HyperOS kills background apps aggressively. For reliable low-frequency tapping, complete **all** of the following:

1. **Enable the Accessibility service**
   Settings → Additional settings → Accessibility → Downloaded apps → "连点器" → enable.
   (The app home screen also offers a shortcut.)

2. **Overlay (floating window) permission**
   Settings → Apps → Manage apps → 连点器 → Display pop-up windows (or "Display over other apps") → Allow.

3. **Exact alarm permission** (required for low-frequency, Android 12+)
   Settings → Apps → Manage apps → 连点器 → Alarms & reminders → Allow.

4. **Notification permission** (needed by the keep-alive notification, Android 13+)
   Settings → Apps → Manage apps → 连点器 → Notifications → Allow.

5. **Auto-start**
   Settings → Apps → Manage apps → 连点器 → Autostart → Allow.
   (The in-app "Open auto-start settings" tries to open the MIUI auto-start page directly, and falls back to app details if that fails.)

6. **Battery optimization set to "No restrictions"**
   Settings → Battery → App battery saver → 连点器 → No restrictions.

> It's also recommended to **lock** the app in Recents (swipe down on the task card to lock it) so it isn't killed by "clear all".

## Usage

1. Open the app and tap **+** to create a new scheme.
2. Enter a name, choose "High-frequency" or "Low-frequency", and set the interval (use quick presets; low-frequency includes **15 min / 30 min / 1 hour**).
3. Choose a repeat mode (infinite / count / duration); for low-frequency tasks that should survive a reboot, turn on "Auto-start on boot", then save.
4. Back on the list, tap **Select** on a scheme card (this makes it the target for point recording).
5. Switch to the target screen, tap the floating ball's **Record** → **tap = normal point**, **hold = long-press point** → tap **Done** at the bottom.
6. Back in the app, fine-tune each point's press duration / gap / order; then on the list tap **Start** to begin clicking.
7. The floating ball lets you **start / stop / hide**, and is draggable.

## Low-frequency behavior

- **Screen off interrupts taps**: a tap injected by `dispatchGesture` must reach the target app while the screen is on; once the screen turns off the input pipeline closes and the tap does nothing. For long low-frequency runs, keep the screen on (the screen is kept awake while the floating window is shown; you can also use Developer options → "Stay awake while charging" or a longer screen timeout) and keep the target app in the foreground.
- **Gap semantics**: the "gap (ms)" after each point is "wait this long after tapping this point before tapping the next point". **The last point's gap is ignored** (there is no next point). The "every X minutes/hours" timer starts counting **after the last point has been tapped**.

## Project structure

```
app/src/main/java/com/hyperclicker/
├── ClickerApp.kt                 # Application, DB/repository entry
├── data/                         # Data layer: Room entity, DAO, repository
│   ├── ClickScheme.kt            #   Scheme entity + point (normalized coord) serialization
│   ├── SchemeDao.kt
│   ├── AppDatabase.kt
│   └── SchemeRepository.kt
├── service/                      # Core accessibility service
│   ├── ClickAccessibilityService.kt  # Gesture injection, high/low-freq, floating ball, point recording
│   ├── KeepAliveService.kt       #   Foreground keep-alive service (prevents killing during low-freq tasks)
│   └── ClickerState.kt           #   Cross-component runtime state
├── scheduler/                    # Low-frequency scheduling
│   ├── AlarmScheduler.kt         #   Exact alarm
│   ├── AlarmReceiver.kt          #   Trigger receiver (bounded retry)
│   └── BootReceiver.kt           #   Boot auto-start restore
├── ui/                           # Compose UI
│   ├── MainActivity.kt
│   ├── SchemeListScreen.kt
│   ├── SchemeEditScreen.kt
│   ├── MainViewModel.kt
│   ├── PointPicker.kt            #   Full-screen point-recording overlay
│   └── theme/
└── util/PermissionUtils.kt       # Permission navigation helpers
```

## Not clicking? Troubleshooting (HyperOS)

If "Start" produces no clicks, check in order:

1. **Tap "Test" on the floating ball first**: it taps the screen center once.
   - If the center button of the target app is tapped → gesture injection works; the issue is the **point coordinates**. Re-record and make sure the recording screen matches the execution screen (resolution/orientation).
   - If "Test" also does nothing → injection is being blocked by the system; continue below.
2. **Toggle the Accessibility service off and on again**: after updating the app, the system sometimes disables the service. Go to Settings → Additional settings → Accessibility → "连点器", **turn it off then on again**, otherwise HyperOS silently drops gestures.
3. **Confirm the floating ball shows**: if it appears, the service is connected; if not, the Accessibility service or overlay permission isn't fully granted.
4. **Set battery to "No restrictions" + allow auto-start + notification permission** (see "Device setup" steps 4, 5, 6), and lock the app in Recents.
5. **Check logs via ADB (optional)**: connect the phone and run `adb logcat -s HyperClicker`; if you see `dispatchGesture returned false` or `dispatchGesture exception` after starting, the system is explicitly rejecting injection.
6. Some apps (banking, payment, security center, etc.) block external gesture injection; verify with a normal app (browser, notes) instead.

## Notes & limitations

- This app only injects tap gestures and **does not collect or upload any screen content**. The accessibility config declares `canRetrieveWindowContent=true` solely to work around HyperOS's restriction on gesture injection (mainstream auto clickers declare the same).
- If the Accessibility service is disabled by the system or user, low-frequency tasks stop; re-enable the service and tap "Start" to resume.
- Some apps (banking, payment, etc.) may have anti-automation detection; whether clicks register depends on the target app.
- Exact alarms are off by default on Android 13/14 — grant the permission in step 3 above, otherwise low-frequency taps won't fire on time.
