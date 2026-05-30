# 迹录 TracePass

迹录 TracePass 是一款 Android NFC 读卡应用。通过手机的 NFC 硬件读取非接触式 IC 卡信息，支持 ISO7816-4、FeliCa 等标准电子钱包协议，可在无读取限制的情况下获取卡片余额、有效期及交易记录等信息。

## 功能特性

- **多卡种支持**：深圳通、北京市政交通卡、武汉通、长安通、上海公共交通卡、香港八达通、银联闪付（QuickPass）、交通联合卡（T-Union）、城市一卡通等
- **卡片信息读取**：应用名称、序列号、版本号、有效期、余额、交易次数、交易记录等
- **便捷分享**：支持复制卡片信息到剪贴板或分享给其他应用
- **沉浸式体验**：适配全面屏、刘海屏，支持沉浸式状态栏和底部导航栏

## 系统要求

- Android 5.0 (API 21) 及以上
- 设备需支持 NFC 功能

## 下载安装

最新版本可在 [GitHub Releases](https://github.com/sinpolib/nfcard/releases) 页面下载。

## 构建项目

本项目使用 Android Studio 和 Gradle 构建：

```bash
./gradlew assembleDebug
```

## 开源协议

迹录 TracePass 基于 GNU GPLv3 协议开源 - 详见 ``LICENSE`` 文件。

---

*原名 NFCard，由 Sinpo Lib 开发*
