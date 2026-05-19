# Moonlight Android (Keyboard Fork)

An unofficial fork of [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) that adds an accessibility-based keyboard service. With the service enabled, physical keyboard shortcuts like `Super`/`Win`, `Alt+Tab`, and `Esc` are forwarded to the host instead of being intercepted by Android.

The keyboard service code is based on [xor-128's PR #1131](https://github.com/moonlight-stream/moonlight-android/pull/1131), which upstream cannot merge for Google Play compliance reasons.

See the [upstream README](https://github.com/moonlight-stream/moonlight-android) for everything else: pairing, host setup, supported devices, and general usage.

## Installing

You have two options: download a prebuilt APK from this repository's CI, or fork the repo and build it yourself.

### Option 1: Download a prebuilt APK

1. Go to the [Actions tab](../../actions/workflows/android.yml).
2. Open the most recent successful run on the `master` branch.
3. Scroll to **Artifacts** and download `Moonlight APK`. You will get a zip containing `app-nonRoot-release-signed.apk`.
4. Transfer the APK to your Android device and install it (`adb install app-nonRoot-release-signed.apk`, or open the file on the device and accept the sideload prompt).

GitHub only serves artifacts to logged-in users, so you need a GitHub account to download.

### Option 2: Build your own by forking

Useful if you don't trust prebuilt binaries, want to modify the source, or the build artifacts have expired (GitHub keeps them for 90 days by default).

1. Click **Fork** at the top of this repository.
2. In your fork, open the **Actions** tab and click **I understand my workflows, go ahead and enable them**.
3. Open the **Android CI** workflow on the left.
4. Click **Run workflow** → select the `master` branch → **Run workflow**. The build takes around 5 minutes.
5. When it finishes, open the run and download `Moonlight APK` from **Artifacts**.
6. Install the APK as in Option 1.

The workflow uses a throwaway signing key generated per run. This means APKs from different runs (or different forks) cannot upgrade each other in place — you must uninstall the previous version first if Android refuses the install with a signature mismatch.

### Option 3: Build locally

If you have Android Studio or just the Android SDK installed, follow the [upstream build instructions](https://github.com/moonlight-stream/moonlight-android#building). The relevant settings:

- JDK 17
- Android SDK platform 34, build-tools 34.0.0
- Android NDK `27.0.12077973`
- `git submodule update --init --recursive` before the first build

## Enabling the keyboard service

The accessibility service is installed but **disabled** by default. You have to turn it on manually:

1. Open Android **Settings** → **Accessibility** (the exact path varies by vendor; on some devices it's under **Additional Settings** or **System**).
2. Find **Moonlight Physical Keyboard Service** in the list of installed services.
3. Toggle it on and accept the system warning.
4. On Android 13 and newer, if the toggle is greyed out, go to **Settings** → **Apps** → **Moonlight** → tap the three-dot menu → **Allow restricted settings**, then try again. This is Android's default protection against sideloaded apps requesting accessibility access.

The service only intercepts key events while a stream is connected. When no stream is active it stays out of the way, and `Volume Up`, `Volume Down`, and `Power` are never forwarded.

## What the service does

While streaming, key events from a connected physical keyboard go to the remote host. Without the service Android handles them first — `Alt+Tab` switches Android apps, `Super` opens the launcher, and so on, never reaching the game. With the service enabled those keys reach the host normally.

This only affects **hardware** keyboards (USB or Bluetooth). Soft keyboard / IME input is unchanged.

The Android back button is also remapped to send `Escape` to the host, regardless of whether the accessibility service is enabled.
