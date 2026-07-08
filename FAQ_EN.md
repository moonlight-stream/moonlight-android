# Moonlight V+ Q&A

This Q&A only includes issues that repeatedly appear in Moonlight V+ reports or have a clear troubleshooting conclusion from issues / pull requests.

For general Moonlight setup, see the upstream [Moonlight setup guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide) and [troubleshooting guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Troubleshooting).

## Before Opening An Issue

Please test with a conservative profile first:

- 1080p, 60 FPS
- H.264 or Auto codec
- HDR off
- 10-20 Mbps bitrate
- Host PC on Ethernet when possible

If this profile works, raise resolution, frame rate, bitrate, HEVC / AV1, HDR, and V+ enhanced features one at a time. This makes it much easier to identify whether the bottleneck is network, decoder, display mode, host-side timing, or a V+ feature.

When reporting a streaming issue, include:

- Moonlight V+ version
- Device model and Android version
- Host app and version: Sunshine, Foundation Sunshine, or GFE
- GPU model and driver version
- Resolution, FPS, codec, HDR, and bitrate
- Network path: LAN, Internet, VPN, EasyTier, Tailscale, ZeroTier, etc.
- Performance overlay values or screenshot
- Crash log, logcat, or ADB diagnostics when available

## Video And Decoders

### Q: H.265 / HEVC shows a black screen on an Android TV box. What should I try first?

Try these checks in order:

1. Switch to H.264 and verify the same stream works.
2. Test HEVC at 1080p60 before trying 1440p, 4K, HDR, or high refresh rates.
3. Turn HDR off and reconnect.
4. Compare with upstream Moonlight if possible.
5. Capture logcat while reproducing the issue.

Some TV boxes advertise HEVC support but fail or become extremely slow with certain resolutions or decoder paths. ONN 4K Pro / S905X-class reports showed HEVC black screen or very low FPS while H.264 worked. PR #289 reduced an Amlogic HEVC black-screen path, but very low HEVC performance can still be device/firmware-specific.

Sources: [#249](https://github.com/qiin2333/moonlight-vplus/issues/249), [#289](https://github.com/qiin2333/moonlight-vplus/pull/289)

### Q: My MTK TV has several seconds of stream latency after updating. Is it a settings issue?

If lowering resolution, lowering FPS, and turning HDR off do not help, the issue may be a device-specific decoder scheduling regression rather than bandwidth or host load.

For the Hisense U7Q case, diagnostics pointed to the MTK HEVC decoder path accumulating display latency. PR #300 removed a risky MTK `KEY_OPERATING_RATE = Short.MAX_VALUE` override while keeping safer low-latency vendor parameters. The reporter confirmed the test build fixed the problem.

Update to a build that includes PR #300 or later. If the problem remains, attach performance overlay data plus ADB diagnostics such as SurfaceFlinger, meminfo, media player state, and logcat.

Sources: [#299](https://github.com/qiin2333/moonlight-vplus/issues/299), [#300](https://github.com/qiin2333/moonlight-vplus/pull/300)

### Q: HDR looks washed out, too bright, too dark, or inconsistent.

First isolate whether the issue is HDR itself:

1. Reconnect with HDR off.
2. Test a standard resolution and refresh rate such as 1080p60.
3. If the stream is stable, re-enable HDR and adjust V+ HDR brightness calibration if available.

Moonlight V+ added manual HDR brightness calibration for devices with inaccurate HDR capability reporting.

Sources: [#374](https://github.com/qiin2333/moonlight-vplus/pull/374), [#395](https://github.com/qiin2333/moonlight-vplus/issues/395)

## Network And Remote Access

### Q: Moonlight V+ cannot discover or add my PC over a virtual LAN / VPN.

First verify local LAN streaming works without the VPN path. Then report:

- VPN or virtual network used: EasyTier, Tailscale, ZeroTier, Clash TUN, etc.
- Whether host discovery fails, manual IP fails, or streaming fails after pairing.
- Whether the issue changed between V+ versions.
- Whether Android is routing the virtual network as a VPN, TUN, SOCKS, or app proxy path.

A V+ regression after 12.6.5 affected virtual-network discovery for some setups. PR #301 fixed VPN virtual LAN device detection, and the reporter confirmed a later build could detect the host again.

Sources: [#297](https://github.com/qiin2333/moonlight-vplus/issues/297), [#301](https://github.com/qiin2333/moonlight-vplus/pull/301)

### Q: EasyTier does not connect when Network Secret is empty.

Prefer setting a Network Secret on the EasyTier server for safety. If you intentionally use an empty secret, use a build that includes the EasyTier empty-secret restoration. The app should make this behavior clear instead of failing silently.

Sources: [#383](https://github.com/qiin2333/moonlight-vplus/issues/383), [#392](https://github.com/qiin2333/moonlight-vplus/pull/392)

## Input

### Q: My USB controller is detected as the wrong controller type.

If the controller works over Bluetooth but is mapped differently over USB, first check Sunshine's controller input setting. In one reported case, switching Sunshine input from Auto to force Xbox resolved the wrong controller type.

When reporting, include the controller model, USB/Bluetooth mode, Android device, Sunshine setting, and whether upstream Moonlight behaves the same.

Source: [#328](https://github.com/qiin2333/moonlight-vplus/issues/328)

### Q: Stylus, pen, or touchpad input behaves incorrectly.

Use a recent build first. V+ has ongoing fixes in this area:

- PR #348 added native precision touchpad support for tablets.
- PR #382 routed stylus input before pointer capture.
- PR #312 fixed S Pen hover coordinate offset when stream resolution and tablet hardware resolution differ.

When reporting stylus/touch issues, include whether the device is a tablet, whether pointer capture is active, stream resolution, physical screen resolution, orientation, and whether an external display is used.

Sources: [#348](https://github.com/qiin2333/moonlight-vplus/pull/348), [#382](https://github.com/qiin2333/moonlight-vplus/pull/382), [#312](https://github.com/qiin2333/moonlight-vplus/pull/312)

## Audio

### Q: Video is fine, but audio has light stutter on Android TV.

Try lowering the host audio output sample rate first. Also test a conservative stream profile such as 1080p60 at a lower bitrate, because some TV SoCs struggle with 4K120/high-bitrate video plus audio processing at the same time.

If audio passthrough is enabled, test with passthrough off, then try a larger passthrough buffer if your receiver setup needs it.

Source: [#298](https://github.com/qiin2333/moonlight-vplus/issues/298)

