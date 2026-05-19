# Moonlight Android

Please refer to the README of the [upstream](https://github.com/moonlight-stream/moonlight-android) first.

The main code of this project comes from [xor-128's Pull request](https://github.com/moonlight-stream/moonlight-android/pull/1131). Thanks to [xor-128](https://github.com/xor-128) for the code and idea.

This is an unofficial fork of Moonlight Android, which adds an accessibility keyboard service not yet supported by the upstream due to Google Play compliance. This allows you to use common key operations such as Win, Esc, Alt+Tab on the remote host without triggering the Android system's keyboard shortcut.

To use this fork, you need to download the pre-built APK file from the latest Actions build entry in this project and install it (of course, you can also pull the source code and build it yourself according to the upstream build method).

After the initial installation, you need to manually open the settings, find the accessibility settings, and manually enable the Moonlight Physical Keyboard Service. After that, you can normally connect to the remote host and use the shortcuts on the physical keyboard.
