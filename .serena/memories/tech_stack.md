# Tech stack

- Android application, mostly Java, with JNI/native code and bundled
  `moonlight-common-c` Git submodule.
- Java source/target 11; build runtime JDK 17.
- Gradle 8.7 wrapper, Android Gradle Plugin 8.5.1.
- compileSdk/targetSdk 34, minSdk 21.
- Android NDK 27.0.12077973 and ndk-build for native components.
- MediaCodec hardware video decode; SurfaceView for direct output; EGL14,
  GLES20, external OES and SurfaceTexture for optional SBS composition.
- Product flavors: `nonRoot` and legacy `root`; the primary development artifact
  is `nonRootDebug`.
- This workstation keeps the no-sudo project toolchain under
  `~/.local/share/moonlight-android-toolchain/{jdk,sdk}`. OpenCode injects it
  into shell commands and Serena MCP through tracked project configuration.
- Serena enables Java first and C/C++ second. Java has a complete Gradle/JDTLS
  model; clangd uses fallback parsing until a compile_commands database is
  deliberately added for ndk-build.
