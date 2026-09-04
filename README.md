# PhoneGuard 手机防火墙 🔒

> 一款功能完整的 Android 手机安全防护工具，应用锁 + 入侵拍照取证 + ADB 防护 + SIM 卡告警

[![API](https://img.shields.io/badge/API-28%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 功能一览

| 功能 | 说明 |
|------|------|
| 🔒 **应用锁** | 指定应用上锁，密码错误时自动用前置摄像头拍照取证 |
| 📸 **ADB 入侵拍照** | 检测到 ADB 调试开启，自动拍照 + 通知告警 |
| 🚨 **入侵检测** | 锁屏失败自动拍照 + 定位 + 警报，阈值可配置 |
| 📡 **SIM 卡防护** | 检测 SIM 卡更换，发送告警通知 |
| 📋 **安全日志** | 全量安全事件记录，可导出查看 |

## 截图

| 首页 | 应用锁 | ADB 防护 | 入侵照片 |
|------|--------|----------|----------|
| ![首页](screenshots/home.png) | ![应用锁](screenshots/applock.png) | ![ADB](screenshots/adb.png) | ![入侵照片](screenshots/intrusion.png) |

## 快速开始

### 前置要求

- Android Studio Hedgehog 2023.1.1+
- Android SDK 34
- Gradle 8.5
- JDK 17

### 构建

```bash
git clone https://github.com/hnpdsyn/PhoneGuard.git
cd PhoneGuard
./gradlew assembleDebug
```

或者直接运行 `build_apk.bat`（Windows 一键打包）。

### 安装

1. 编译生成 `app/build/outputs/apk/debug/app-debug.apk`
2. 安装到 Android 9+ 设备
3. 开启无障碍服务：设置 → 无障碍 → 已安装的应用 → 手机防火墙 → 开启
4. 授予相机权限（用于拍照取证）
5. 关闭电池优化，保障后台运行

## 技术栈

- **语言:** Kotlin 1.9
- **UI:** Jetpack Compose 1.5.4
- **架构:** Material 3 + 无障碍服务 + Camera API
- **最低支持:** Android 9 (API 28)
- **目标 SDK:** 34

## 兼容性说明

- 国产 ROM（vivo OriginOS、小米 MIUI、华为 EMUI 等）对后台服务限制严格，需开启**自启动 + 忽略电池优化 + 后台锁定**
- 已测试 Android 9-14 系统

## 变现渠道

- **闲鱼销售:** 个人版 29.9 元 / 源码版 69 元
- **企业定制:** 源码授权 + 定制开发，2~5 万元起
- **Google Play:** 可上架订阅制

## 开源协议

本项目基于 MIT 协议开源，详见 [LICENSE](LICENSE)。

---

**如果觉得有用，欢迎 ⭐ Star 支持！**