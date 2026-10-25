# 光遇每日任务 · 安卓客户端

查看《光·遇》国服每日任务、季节蜡烛、天气、日历和公告。支持 Android 9 及以上版本。

## 使用

- **今日**：查看任务与季节蜡烛指南。点击任务卡查看攻略，点击勾选框记录完成进度；进度在北京时间每日 00:00 重置。
- **资讯**：查看老奶奶食堂饭点、天气、日历和公告。点「提醒设置」配置提醒；图片支持点击查看大图、双指缩放、双击放大和拖动查看。
- **记录**：查看最近 30 天的历史内容。历史任务只读，设置入口也在这里。
- **设置**：切换主题、管理悬浮球和缓存、设置饭点提醒、查看应用版本，并检查更新。自动主题在北京时间 19:00–07:00 使用深色。

已查看的内容支持离线查看，联网后可点击刷新获取最新内容。

## 应用更新

在「设置 → 关于」点击「检查更新」，按提示下载并安装新版本。覆盖安装会保留已有数据，下载过程中可以取消。

## 饭点提醒

饭点按北京时间计算：08:00、10:00、12:00、16:00、18:00、20:00、22:00 各 30 分钟。在「设置 → 饭点提醒」中选择需要提醒的饭点，并打开提醒开关。可设置提前 0–60 分钟提醒（默认 5 分钟）；取消勾选或关闭开关即可取消对应提醒。

无需一直打开应用。请在系统设置中开启通知与「闹钟和提醒」授权；若设备限制后台运行，还需允许本应用自启动与后台运行。强制停止应用后，需要手动打开一次恢复提醒。

## 悬浮球

1. 打开「记录 → 设置 → 悬浮窗」，按提示允许「显示在其他应用上层」。
2. 返回应用，点击「显示」开启悬浮球。
3. 点按悬浮球查看任务，拖动可调整位置；点击面板外或「关闭」收起。
4. 如需只在游戏内显示，开启「仅在游戏内显示」，点击「允许使用情况访问」并在系统设置中授权。

「仅在游戏内显示」默认关闭。开启后，仅《光·遇》国服处于前台时显示悬浮球；退出游戏或切到其他应用时，悬浮球和已展开的任务面板一起隐藏，返回游戏后自动恢复。锁屏或未授予使用情况访问权限时保持隐藏。自动隐藏不会关闭悬浮服务；如需停用，在设置中点击「关闭」。

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

版本号由构建参数决定，未指定时为 `0.1.0`（`versionCode` 1）。正式签名读取 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，四项齐全时使用该密钥，否则沿用 debug 签名：

```sh
./gradlew :app:assembleRelease -PappVersionName=0.2.0 -PappVersionCode=2
```

构建完成后回到仓库根目录，对安装包做一次重压缩、对齐与重签：

```sh
ANDROID_KEYSTORE_PATH=... ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=... ANDROID_KEY_PASSWORD=... \
node scripts/compress-apk.mjs app/app/build/outputs/apk/release/app-release.apk
```

命令会覆盖原 APK，签名密钥须与现有安装包一致。

## 许可证与内容来源

客户端代码采用 [MIT License](../LICENSE)。

游戏图文来自游戏内「小精灵」，版权归原权利人所有。
