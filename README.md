# Iris

[![Build Iris](https://github.com/atgehrhardt/iris/actions/workflows/build.yml/badge.svg)](https://github.com/atgehrhardt/iris/actions/workflows/build.yml)

Iris is a controller-focused fork of
[Moonlight Android](https://github.com/moonlight-stream/moonlight-android) for
streaming from Prism. It retains Moonlight's controller gyro, handheld motion
fallback, controller rumble, and trigger-rumble support while adding
device-agnostic calibration for up to four independent rear controls.

In Iris, open **Settings → Gamepad Settings → Rear button calibration**. Select
the primary controller, choose how many rear controls the device has, and press
each control in the order it should be exposed. One to four controls are
supported, and calibration defaults to the common two-button layout. A control
may originate from the primary controller or a separate Android input device.
Iris sends the results through Moonlight's existing four-paddle protocol fields.
Prism automatically creates a virtual DualSense Edge, where Linux and Steam see
those slots as Fn1, Fn2, left paddle, and right paddle.

For handhelds whose built-in controls do not expose their own Android vibrator,
enable **Emulate rumble support with vibration** in Gamepad Settings. Iris then
advertises standard rumble for player one and routes host feedback to the
handheld vibrator. A calibrated primary controller is announced as soon as the
stream connects, allowing Steam to discover it without waiting for the first
button or stick event.

Iris uses the application ID `dev.prism.iris`, so it installs alongside the
official Moonlight app. The `upstream` Git remote fetches Moonlight Android and
has pushing disabled. See
[the controller architecture](docs/controller-architecture.md) for the protocol
boundary, auxiliary-device routing, and why InputPlumber is optional.

## Building Iris

Initialize submodules and build the non-root APK:

```shell
git submodule update --init --recursive
./gradlew assembleNonRootDebug
```

Release builds can be signed by setting `IRIS_KEYSTORE_FILE`,
`IRIS_KEYSTORE_PASSWORD`, `IRIS_KEY_ALIAS`, and `IRIS_KEY_PASSWORD`. GitHub tag
builds use repository secrets with the same password and alias names, plus an
`IRIS_KEYSTORE_BASE64` secret containing the base64-encoded keystore. The
workflow's `signed_release` manual option builds and verifies the signed assets
without publishing them. Version-matched tag builds are published as APK
release assets and can be followed by Obtainium.

## Upstream project

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Moonlight downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building upstream Moonlight
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
