# AndroidX Test runner loads Kotlin runtime facades reflectively from the target APK.
# Keep them in minified debug builds so on-device instrumentation tests can start.
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class androidx.compose.** { *; }

# Cross-APK instrumentation tests inspect dialog recreation and focus state.
-keepclassmembers class com.limelight.utils.AboutDialogLauncher {
	*** dialogSnapshot*(...);
}
-keep class com.limelight.utils.AboutDialogLauncher$DialogSnapshot { *; }
