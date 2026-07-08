<div align="center">
  <img src="./app/src/main/res/drawable/vplus.webp" width="100" alt="Moonlight V+ Logo">

  # Moonlight V+

  [![GitHub Release](https://img.shields.io/github/v/release/qiin2333/moonlight-vplus?label=latest&style=flat-square)](https://github.com/qiin2333/moonlight-vplus/releases/latest)
  [![Android](https://img.shields.io/badge/Android-5.0+-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions)
  [![License](https://img.shields.io/badge/license-GPL%20v3-EF9421?style=flat-square)](LICENSE.txt)
  [![GitHub Stars](https://img.shields.io/github/stars/qiin2333/moonlight-vplus?style=flat-square)](https://github.com/qiin2333/moonlight-vplus/stargazers)
  [![Downloads](https://img.shields.io/github/downloads/qiin2333/moonlight-vplus/total?style=flat-square&color=blue)](https://github.com/qiin2333/moonlight-vplus/releases)

  **An enhanced Android game streaming client based on [Moonlight](https://github.com/moonlight-stream/moonlight-android)**

  English | [中文](README.md)

</div>

---

## Screenshots

<div align="center">
  <img src="https://github.com/user-attachments/assets/bb174547-9b0d-4827-81cf-59308f3cfa9e" width="640" alt="Home">
  <br/>
  <img src="https://github.com/user-attachments/assets/5e227bab-afaa-4452-ae38-ac2cc9d22988" width="240" alt="App List">
  <img src="https://github.com/user-attachments/assets/9101bf19-782e-4c6f-977f-34b138b93990" width="240" alt="Streaming">
  <img src="https://github.com/user-attachments/assets/5046dd58-7013-494e-9f17-26e4de56a7dd" width="240" alt="Settings">
</div>

## What's Different from Upstream Moonlight

Moonlight V+ extends [moonlight-android](https://github.com/moonlight-stream/moonlight-android) with a wide range of enhancements while maintaining full compatibility with the upstream streaming protocol.

| Category | Feature | Description | Since |
|----------|---------|-------------|:-----:|
| **Streaming** | Ultra-high refresh rate | Unlock 144 / 165 Hz, up to 800 Mbps bitrate | |
| | HDR / HLG | Auto-load device-specific HDR calibration profiles, HLG support | `12.6.6` |
| | Custom resolution | Arbitrary resolution, aspect ratio, and asymmetric resolution | |
| | Multi-scene presets | Save and switch streaming configs per game with one tap | `12.3` |
| **Input** | Custom on-screen keys | Drag / resize / hide buttons; combos, turbo fire, gamepad aiming | `12.3.3` |
| | Multi-profile | Multiple key layouts with real-time profile switching | |
| | Wheel pad | Radial menu sectors with custom key bindings | `12.3.7` |
| | Enhanced touch | Stylus, pen, multi-touch, and trackpad mode | `12.3.10` |
| | Motion assist | Gyroscope aim / look with adjustable sensitivity | `12.3.3` |
| | Multi-gamepad | Auto-detect Xbox / PS / Switch / third-party controllers | `12.5.3` |
| **UI** | App desktop polish | Thumbnail backgrounds, custom sorting | |
| | Action cards | Quick-access shortcuts, commands, and performance panels | |
| | Live bitrate tuning | Adjust bitrate from the in-stream menu without disconnecting | `12.3.10` |
| | Floating ball | Gesture-based shortcut bubble | `12.7.3` |
| | QR pairing | Scan QR code to pair with host | `12.7.4` |
| **Streaming+** | External display | One-tap secondary screen with rotation sync | `12.6.5` |
| | Keep-alive | Switch apps without tearing down the stream | `12.6.6` |
| | Multi-screen select | Choose which host screen to stream | `12.5.0` |
| **Monitoring** | Performance overlay | FPS, 1% low FPS, bitrate, latency, packet loss, etc. | `12.4.1` |
| **Audio** | Mic redirect | Remote voice chat (requires Foundation Sunshine) | `12.3.12` |
| | 7.1.4 surround | Atmos spatial audio support | `12.7.4` |
| | Audio vibration | Real-time bass energy drives haptic feedback (device / gamepad / both) | `12.7.0` |
| | | Three scene modes: Game (sustained rumble), Music (beat pulses), Auto | |

## Getting Started

### Requirements

- Android 5.0+ (API 22)
- Device with HEVC / AV1 hardware decoding (recommended)
- 5 GHz / 6 GHz Wi-Fi or wired LAN connection
- A host PC running [Sunshine](https://github.com/LizardByte/Sunshine), [Foundation Sunshine](https://github.com/qiin2333/foundation-sunshine), or legacy NVIDIA GameStream

### Installation

Download the latest APK from [Releases](https://github.com/qiin2333/moonlight-vplus/releases/latest), install it, and pair it with your host.

For the first stream, start with a conservative profile:

- 1080p, 60 FPS
- H.264 or Auto codec
- HDR off
- 10-20 Mbps bitrate
- Host PC connected by Ethernet when possible

After that is stable, raise resolution, frame rate, bitrate, HEVC / AV1, HDR, and V+ enhanced options one at a time.

### Host Choice

- **Sunshine**: recommended for most users and compatible with standard Moonlight streaming.
- **Foundation Sunshine**: recommended if you want V+ enhanced features such as microphone redirection, host display control, live bitrate control, app desktop polish, and super commands.
- **GeForce Experience / NVIDIA GameStream**: legacy path. It may still work on old setups, but Sunshine is the safer default for new users.

### If Something Goes Wrong

- Black screen or decoder crash: try H.264, turn HDR off, lower resolution / FPS, then reconnect.
- Stutter or "slow connection": lower bitrate first, then check Wi-Fi quality, packet loss, and latency variance in the performance overlay.
- Android TV / TV box issues: test 1080p60 H.264 before trying HEVC, AV1, HDR, or high refresh rates.
- Internet streaming issues: verify local LAN streaming works before configuring port forwarding, UPnP, EasyTier, Tailscale, or other VPN paths.

Useful references:

- [Moonlight V+ Q&A](FAQ_EN.md)
- [Moonlight setup guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide)
- [Moonlight troubleshooting guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Troubleshooting)

### Building from Source

```bash
git clone https://github.com/qiin2333/moonlight-vplus.git
cd moonlight-vplus
./gradlew assembleRelease
```

## Foundation Sunshine Enhanced Features

The following features require **[Foundation Sunshine](https://github.com/qiin2333/foundation-sunshine)** on the host side:

| Feature | Description | Min Version |
|---------|-------------|-------------|
| Mic redirect | Low-latency microphone audio forwarding to the host | 2025.0720+ |
| Live bitrate adjustment | Dynamically tune video bitrate during a session | — |
| Super menu commands | Send advanced control commands to the host from the in-stream menu | — |
| App desktop polish | Sync host app icons; custom sorting and grouping | — |
| Host auto-optimization | Auto-negotiate resolution/DPI, touch keyboard, and state memory | — |

## Contributing

Issues and Pull Requests are welcome!

### Contributors

| Contributor | Focus Area |
|-------------|------------|
| [@cjcxj](https://github.com/cjcxj) | Custom keys, touch menu, accessibility |
| [@alonsojr1980](https://github.com/alonsojr1980) | SoC decoder optimization |
| [@Xmqor](https://github.com/Xmqor) | Gamepad aiming |
| [@TrueZhuangJia](https://github.com/TrueZhuangJia) | Enhanced multi-touch |
| [@WACrown](https://github.com/WACrown) | Custom key system |

## Acknowledgments

- [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) — upstream project
- [Sunshine](https://github.com/LizardByte/Sunshine) — open-source game streaming host

## License

This project is licensed under the [GPL v3](LICENSE.txt).

---

<div align="center">
  <sub>Find it useful? Give us a ⭐!</sub>
</div>
