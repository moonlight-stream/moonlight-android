# Suggested commands

Initialize native sources:

```bash
git submodule update --init --recursive
```

Build the primary debug APK:

```bash
./gradlew assembleNonRootDebug
```

Run local verification:

```bash
./gradlew test lint
git diff --check
```

Build both debug flavors when root-flavor compatibility matters:

```bash
./gradlew assembleDebug
```

APK output:
`app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk`.

OpenCode injects the user-local JDK/SDK from
`~/.local/share/moonlight-android-toolchain`. Outside OpenCode, export
`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` and prepend the JDK `bin` to
`PATH`, or install the equivalent system toolchain.

Device installation is an external side effect and requires user approval:

```bash
adb install -r app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
```
