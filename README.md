# 极简冰箱 (IceBox for Android)

[中文说明](#中文说明) | [English](#english)

---

## 中文说明

一款轻量级、高颜值的 Android 应用冻结工具。基于 Kotlin + Jetpack Compose 构建，采用 Material Design 3 Expressive (MD3E) 设计语言，底层通过常驻 Root Shell 调用 `pm disable` / `pm enable` 实现极速冻结与解冻。

### 核心特性

- 冰箱列表管理：点击右上角 `+` 自由勾选需要管控的应用，支持关键字搜索，长按列表卡片可快速移出。
- 一键批量冻结：通过单条常驻 `su` 会话批量执行冻结命令，毫秒级响应，告别逐个操作的繁琐。
- 点击解冻直达：点击已冻结的应用卡片，自动解冻并直接启动；也可点击卡片右侧快捷按钮单独切换状态。
- 丝滑高刷体验：启动时主动向系统申请最高屏幕刷新率，配合后台异步预解码与缓存应用图标，列表滑动零掉帧。
- 动态取色与深色模式：完整适配 Android 12+ 壁纸动态取色（Material You）与系统深浅色主题切换。

### 环境要求

- 系统版本：Android 8.0 (API 26) 及以上
- 权限要求：设备已获取 Root 权限（支持 Magisk / KernelSU / APatch）

### 使用说明

1. 首次打开应用，点击右上角 `+` 按钮，在弹出列表中勾选需要冻结的应用。
2. 点击右下角「一键冻结」悬浮按钮，即可批量冻结列表内所有正在运行的应用。
3. 需要临时使用时，直接在冰箱列表中点击对应应用，软件会自动解冻并跳转打开；用完后返回一键冻结即可。
4. 如需将某个应用移出冰箱列表，长按该应用卡片即可。

### 本地构建

本项目使用标准 Gradle Kotlin DSL 配置，克隆仓库后可直接使用 Android Studio 打开，或在配置有 JDK 17 与 Android SDK 的终端中执行：

    git clone https://github.com/MOUAJMD/IceBox_for_Android.git
    cd IceBox_for_Android
    ./gradlew assembleDebug

编译生成的安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 开源协议

本项目基于 MIT License 开源。

---

## English

A lightweight, Material Design 3 Expressive (MD3E) Android application freezer built with Kotlin and Jetpack Compose. It utilizes a persistent Root Shell (`pm disable` / `pm enable`) for instant batch freezing and one-tap unfreeze-to-launch.

### Features

- Curated Freeze List: Tap the top-right `+` button to select apps to manage, with instant keyword search and long-press removal.
- One-Tap Batch Freeze: Executes batch disable commands through a single persistent `su` session for millisecond-level response.
- Tap to Unfreeze & Launch: Tapping a frozen app automatically re-enables and launches it immediately; individual toggle buttons are also available on each card.
- High Refresh Rate & Smooth Scrolling: Requests the device's maximum supported display refresh rate on startup and pre-decodes/caches app icons asynchronously on IO threads.
- Dynamic Color & Dark Mode: Full support for Android 12+ Material You dynamic wallpaper theming and system dark mode.

### Requirements

- Android 8.0 (API 26) or higher
- Root access (Magisk / KernelSU / APatch)

### Usage

1. Open the app and tap the top-right `+` button to add apps to your freeze list.
2. Tap the bottom-right "Freeze All" floating button to freeze all active apps in the list at once.
3. Tap any frozen app card in the list to automatically unfreeze and launch it.
4. Long-press any app card to remove it from the freeze list.

### Build from Source

Clone the repository and build with Gradle (JDK 17 and Android SDK required):

    git clone https://github.com/MOUAJMD/IceBox_for_Android.git
    cd IceBox_for_Android
    ./gradlew assembleDebug

The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### License

This project is licensed under the MIT License.
