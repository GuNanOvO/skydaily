# 光遇每日任务 · 安卓客户端

查看《光·遇》国服每日任务、季节蜡烛、天气、日历和公告。支持 Android 9 及以上版本。

## 使用

- **今日**：查看任务与季节蜡烛指南。点击任务卡查看攻略，点击勾选框记录完成进度；进度在北京时间每日 00:00 重置。
- **资讯**：查看天气、日历与公告。点击图片查看大图，支持双指缩放、双击放大和拖动查看。
- **记录**：查看最近 30 天的历史内容。历史任务只读，设置入口也在这里。
- **设置**：切换主题、管理悬浮球和缓存、查看应用版本及反馈入口。自动主题在北京时间 19:00–07:00 使用深色。

已查看的内容会保存在本地，离线时可查看缓存；恢复联网后可点击刷新获取最新内容。

## 悬浮球

1. 打开「记录 → 设置 → 悬浮窗」，按提示允许「显示在其他应用上层」。
2. 返回应用，点击「显示」开启悬浮球。
3. 点按悬浮球查看任务，拖动可调整位置；点击面板外或「关闭」收起。

## 构建与安装

需要 JDK 21，以及 Android SDK 的 `platforms;android-37.0` 和 `build-tools;37.0.0`。在 `app/local.properties` 中配置 `sdk.dir`，或设置 `ANDROID_HOME`。

在仓库根目录执行：

```sh
cd app
./gradlew :app:assembleRelease     # 生成安装包
./gradlew :app:assembleDebug       # 生成调试包
./gradlew :app:testDebugUnitTest   # 运行单元测试
```

发行安装包位于 `app/build/outputs/apk/release/app-release.apk`（相对于上面的 `app` 目录）。可以复制到手机安装，或执行：

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 内容来源

游戏图文来自游戏内「小精灵」，版权归原权利人所有。
