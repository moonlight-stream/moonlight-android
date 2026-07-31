# Moonlight Android — SBS Headset Fork

Turn an Android phone and a passive Cardboard-style headset into a low-latency,
head-controlled virtual display for a Sunshine-powered PC.

This project is an experimental fork of
[Moonlight for Android](https://github.com/moonlight-stream/moonlight-android).
It keeps the normal Moonlight streaming experience and adds an optional local
side-by-side (SBS) compositor for inexpensive phone headsets such as the
SHINECON SC-G12.

> [!IMPORTANT]
> This is not stereoscopic VR. Both eyes receive the same flat desktop stream.
> There is no OpenXR runtime, virtual room, stereo camera, second network stream,
> or Sunshine protocol extension.

## Why this exists

A phone already contains a high-resolution display, orientation sensors, Wi-Fi,
and a hardware video decoder. A passive headset provides the lenses and mounting.
This fork combines them into a private panoramic monitor for desktop work,
browsing, flat games, and relaxed couch or bed use.

The wide PC desktop is rendered once by the host and decoded once by the phone.
The local compositor duplicates the frame for both eyes. Optional head tracking
moves the already calibrated pair of images, allowing areas outside the central
lens view to be inspected naturally.

## Features

### Low-latency SBS output

- One normal Moonlight network stream.
- One hardware `MediaCodec` decoder.
- One external OES texture and one OpenGL ES composition pass.
- No CPU frame copies.
- The same flat frame is drawn into independent left and right eye viewports.
- At 100% scale, the image covers each eye viewport without stretching; hidden
  areas remain accessible through head-controlled panning.
- The upstream direct-to-Surface renderer remains the default when SBS is off.

### Head-controlled viewport

- Optional master switch, disabled by default.
- Relative yaw and pitch tracking from the phone orientation sensor.
- Independent enable switches for horizontal and vertical movement.
- Separate horizontal and vertical sensitivity.
- Configurable edge reach, from strict image bounds to bringing an edge into
  the center of the lens.
- Automatic centering when tracking starts and manual recentering from the web UI.
- Sensor-only redraws reuse the latest decoded frame instead of consuming another
  decoder frame.

### Per-headset and per-eye calibration

- Virtual screen scale, eye separation, and vertical placement.
- Common and per-eye horizontal offsets.
- Independent vertical offsets for each eye.
- Common yaw and pitch with per-eye correction.
- Independently switchable horizontal and vertical lens correction.
- Independently switchable signed horizontal and vertical chromatic-aberration
  correction.
- Saved calibration remains intact when head tracking is enabled, disabled, or
  recentered.

### Live web calibration

An optional LAN calibration page can preview geometry changes while the stream
is running:

- Live preview without writing preferences.
- Save, revert, and reset actions.
- A dedicated **Center view** button.
- The complete calibration snapshot is saved together, so adjusting head
  tracking does not discard per-eye settings.

> [!WARNING]
> The calibration server has no authentication or TLS. Enable it only on a
> trusted local network and disable it when calibration is complete.

## Rendering paths

| | Normal mode | SBS headset mode |
| --- | --- | --- |
| Network streams | 1 | 1 |
| Hardware decoders | 1 | 1 |
| CPU frame copies | 0 | 0 |
| Output | Decoder directly to display Surface | Decoder to OES texture, then one GLES pass |
| Eye images | Normal fullscreen image | Same flat image drawn twice |
| HDR | Upstream behavior | Disabled for now |

The SBS resource owner is deliberately separate from the codec:

```text
Sunshine host
    |
    | one Moonlight video stream
    v
MediaCodec hardware decoder
    |
    | one decoder Surface
    v
SurfaceTexture / external OES texture
    |
    | one OpenGL ES pass
    +-------------------+
    |                   |
    v                   v
left eye viewport    right eye viewport
```

`Game` continues to own Activity, Surface, and connection lifecycle.
`MediaCodecDecoderRenderer` owns decoding, pacing, and recovery.
`SbsRenderer` owns EGL, the OES texture, `SurfaceTexture`, decoder
`Surface`, render thread, and SBS composition. `SbsHeadTracker` owns the
sensor thread and relative-pose baseline.

## Getting started

### Requirements

- A Sunshine-compatible host PC.
- An Android phone with a gyroscope or rotation-vector sensor.
- A passive Cardboard-style phone headset.
- A low-latency local network; Ethernet from the host to the router is recommended.

The first validated setup is:

- OnePlus 11
- SHINECON SC-G12
- HEVC hardware decoding with `c2.qti.hevc.decoder.low_latency`
- 3072×1440 at 60 FPS

### Build the APK

Required toolchain:

- JDK 17
- Android SDK 34
- Android Build Tools 34.0.0
- Android NDK 27.0.12077973
- Initialized Git submodules

```bash
git clone --recurse-submodules https://github.com/Bogdan7c/moonlight-android.git
cd moonlight-android
./gradlew assembleNonRootDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
```

Install it on a connected Android device:

```bash
adb install -r app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
```

### Enable SBS mode

1. Open Moonlight settings and enter **Streaming settings**.
2. Enable **Side-by-side headset mode (Experimental)**.
3. Start with **SBS virtual screen size** at 100%.
4. Connect to the host as usual.
5. Put the phone into the headset and calibrate both eye images.
6. Enable **Head-controlled SBS viewport** when the static image is comfortable.

SBS is a local client preference. It does not alter Sunshine, stream negotiation,
or another Moonlight client.

## Recommended calibration order

1. **Screen size and position** — set scale, separation, and vertical position.
2. **Pair alignment** — use the common horizontal offset to move both images.
3. **Per-eye alignment** — correct horizontal/vertical differences between eyes.
4. **Perspective** — adjust common yaw/pitch, then per-eye corrections.
5. **Lens geometry** — tune horizontal and vertical correction until straight
   lines look natural.
6. **Chromatic correction** — reduce visible red/blue separation near lens edges.
7. **Head tracking** — enable the required axes, tune sensitivity and edge reach,
   then center the view.

For faster calibration from another device:

1. Enable **SBS calibration server** in Streaming settings.
2. Open the displayed LAN URL in a browser on the same network.
3. Preview changes live.
4. Press **Save** only when the image is comfortable.

Web recentering changes only the runtime head-pose baseline. It does not save or
reset image calibration.

## Validation

The current implementation has passed:

```bash
./gradlew test lint
./gradlew assembleNonRootDebug
```

Unit tests cover calibration defaults and persistence, legacy preference
migration, HTTP parsing and JSON state, per-axis correction switches,
head-tracking axis mapping, sensitivity and edge limits, vertical direction,
homography identity, and full-viewport cover scaling.

The validated OnePlus 11 setup sustained a 3072×1440 HEVC stream at approximately
60 rendered FPS. The headset configuration has also been used successfully for
normal desktop browsing and remote-desktop interaction, not only as a rendering
demo.

## Known limitations

- SBS mode is experimental and currently validated primarily on one phone/headset pair.
- Both eyes display the same mono source; there is no true stereo depth.
- HDR is disabled in SBS mode.
- Lens profiles are calibrated manually; Cardboard QR profiles are not imported.
- OpenXR, Cardboard SDK integration, positional tracking, hand tracking, and a
  virtual environment are intentionally out of scope.
- The web calibration server is a trusted-LAN prototype without authentication,
  TLS, or discovery.
- More device testing is needed for codec variations, Surface recreation,
  background/foreground transitions, and unusual display aspect ratios.

## Upstream status

The SBS work is currently maintained in this fork and is not part of official
Moonlight Android releases. The upstream architecture proposal is tracked in
[moonlight-stream/moonlight-android#1587](https://github.com/moonlight-stream/moonlight-android/issues/1587).

For the official client, downloads, documentation, and support, visit:

- [Moonlight Android](https://github.com/moonlight-stream/moonlight-android)
- [Moonlight website](https://moonlight-stream.org)
- [Moonlight Discord](https://moonlight-stream.org/discord)

## Testing and contributions

Reports from other Android phones and headsets are especially valuable. Please
include:

- Phone model and Android version
- Headset model
- Codec, stream resolution, and frame rate
- Whether normal and SBS modes both work
- Results of reconnect, background/foreground, and display recreation
- Any visible stretching, inversion, clipping, or extra latency

Changes should preserve the core invariants: one stream, one hardware decoder,
no CPU frame copies, GL calls only on the render thread, safe decoder-Surface
teardown, and an untouched direct rendering path when SBS is disabled.

## Original Moonlight authors

- [Cameron Gutman](https://github.com/cgutman)
- [Diego Waxemberg](https://github.com/dwaxemberg)
- [Aaron Neyer](https://github.com/Aaronneyer)
- [Andrew Hennessy](https://github.com/yetanothername)

Moonlight began as a student project at
[MHacks](https://en.wikipedia.org/wiki/MHacks) by students from
[Case Western Reserve University](https://case.edu).
