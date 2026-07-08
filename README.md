<div align="center">
  <img src="./app/src/main/res/drawable/vplus.webp" width="100" alt="Moonlight V+ Logo">

  # Moonlight V+

  [![GitHub Release](https://img.shields.io/github/v/release/qiin2333/moonlight-vplus?label=latest&style=flat-square)](https://github.com/qiin2333/moonlight-vplus/releases/latest)
  [![Android](https://img.shields.io/badge/Android-5.0+-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions)
  [![License](https://img.shields.io/badge/license-GPL%20v3-EF9421?style=flat-square)](LICENSE.txt)
  [![GitHub Stars](https://img.shields.io/github/stars/qiin2333/moonlight-vplus?style=flat-square)](https://github.com/qiin2333/moonlight-vplus/stargazers)
  [![Downloads](https://img.shields.io/github/downloads/qiin2333/moonlight-vplus/total?style=flat-square&color=blue)](https://github.com/qiin2333/moonlight-vplus/releases)

  **基于 [Moonlight](https://github.com/moonlight-stream/moonlight-android) 的增强版 Android 游戏串流客户端**

  [English](README_EN.md) | 中文

</div>

---

## 截图

<div align="center">
  <img src="https://github.com/user-attachments/assets/bb174547-9b0d-4827-81cf-59308f3cfa9e" width="640" alt="主界面">
  <br/>
  <img src="https://github.com/user-attachments/assets/5e227bab-afaa-4452-ae38-ac2cc9d22988" width="240" alt="游戏列表">
  <img src="https://github.com/user-attachments/assets/9101bf19-782e-4c6f-977f-34b138b93990" width="240" alt="串流界面">
  <img src="https://github.com/user-attachments/assets/5046dd58-7013-494e-9f17-26e4de56a7dd" width="240" alt="设置界面">
</div>

## 与上游 Moonlight 的区别

Moonlight V+ 在 [moonlight-android](https://github.com/moonlight-stream/moonlight-android) 的基础上增加了大量实用功能，同时保持与原版串流协议的完全兼容。

| 类别 | 功能 | 说明 | 起始版本 |
|------|------|------|:--------:|
| **串流** | 超高刷新率 | 解锁 144 / 165 Hz，最高 800 Mbps 码率 | |
| | HDR / HLG | 自动加载设备专属 HDR 校准文件，支持 HLG | `12.6.6` |
| | 自定义分辨率 | 任意分辨率、宽高比、不对称分辨率 | |
| | 多场景预设 | 一键保存/切换不同游戏串流配置 | `12.3` |
| **输入** | 自定义按键 | 拖动/缩放/隐藏，支持组合键、连发、手柄瞄准 | `12.3.3` |
| | 多配置档案 | 按键布局支持多配置实时切换 | |
| | 轮盘按键 | 轮盘分区自定义按键绑定 | `12.3.7` |
| | 增强触控 | 触控笔、手写笔、多点触控、触控板模式 | `12.3.10` |
| | 体感辅助 | 陀螺仪体感瞄准 / 视角，灵敏度可调 | `12.3.3` |
| | 多手柄 | Xbox / PS / Switch / 国产手柄自动识别 | `12.5.3` |
| **界面** | 应用桌面美化 | 缩略图背景同步、自定义排序 | |
| | 功能卡片 | 自定义快捷操作、快捷指令、性能面板 | |
| | 实时码率调节 | 菜单内直接调节码率，无需断开连接 | `12.3.10` |
| | 悬浮球 | 手势动作配置，快捷交互入口 | `12.7.3` |
| | QR 配对 | 扫码快速配对主机 | `12.7.4` |
| **串流增强** | 外接显示器 | 一键副屏通道，旋转双向同步 | `12.6.5` |
| | 不断开连接 | 切换应用无需重新建立串流 | `12.6.6` |
| | 多屏幕选择 | 选择主机屏幕进行串流 | `12.5.0` |
| **监控** | 性能覆盖层 | 帧率、1% Low 帧、码率、延迟、丢包等 | `12.4.1` |
| **音频** | 麦克风重定向 | 远程语音通话（需配合 Foundation Sunshine） | `12.3.12` |
| | 7.1.4 环绕声 | Atmos 空间音频支持 | `12.7.4` |
| | 音频震动 | 实时低频能量驱动触觉反馈（设备 / 手柄 / 双路） | `12.7.0` |
| | | 三种场景模式：游戏（持续低频）、音乐（节拍脉冲）、自动识别 | |

## 快速开始

### 系统要求

- Android 5.0+ (API 22)
- 支持 HEVC / AV1 硬解的设备（推荐）
- 局域网 5 GHz Wi-Fi 或有线连接

### 安装

从 [Releases](https://github.com/qiin2333/moonlight-vplus/releases/latest) 下载最新 APK，安装后按应用内引导完成配对即可。

如果遇到黑屏、卡顿、HDR、手柄、EasyTier 等问题，请先查看 [Moonlight V+ 常见问题](FAQ.md)。

### 从源码编译

```bash
git clone https://github.com/qiin2333/moonlight-vplus.git
cd moonlight-vplus
./gradlew assembleRelease
```

## Foundation Sunshine 增强功能

以下功能需要搭配 **[Foundation Sunshine](https://github.com/qiin2333/foundation-sunshine)**（基地版 Sunshine）使用：

| 功能 | 说明 | 最低版本 |
|------|------|----------|
| 麦克风重定向 | 设备麦克风音频实时传输至主机，低延迟高音质 | 2025.0720+ |
| 实时码率调整 | 串流中动态调节码率，网络波动自动适应 | — |
| 超级菜单指令 | 从串流菜单向主机发送高级控制指令 | — |
| 应用桌面美化 | 自动同步主机应用图标，自定义排序与分组 | — |
| 主机自动优化 | 自动协商分辨率/DPI、适配触屏键盘、状态记忆 | — |

## 贡献

欢迎提交 Issue 和 Pull Request！

### 贡献者

| 贡献者 | 方向 |
|--------|------|
| [@cjcxj](https://github.com/cjcxj) | 按键自定义、触控菜单、无障碍 |
| [@alonsojr1980](https://github.com/alonsojr1980) | SoC 解码优化 |
| [@Xmqor](https://github.com/Xmqor) | 手柄瞄准 |
| [@TrueZhuangJia](https://github.com/TrueZhuangJia) | 增强多点触控 |
| [@WACrown](https://github.com/WACrown) | 自定义按键系统 |

## 致谢

- [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) — 上游项目
- [Sunshine](https://github.com/LizardByte/Sunshine) — 开源串流主机端

## 许可证

本项目基于 [GPL v3](LICENSE.txt) 许可证开源。

---

<div align="center">
  <sub>觉得有用？给个 ⭐ 支持一下吧！</sub>
</div>
