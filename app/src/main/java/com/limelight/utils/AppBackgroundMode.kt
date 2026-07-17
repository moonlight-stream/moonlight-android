package com.limelight.utils

import android.content.Context

/** AppView 与串流连接页共用的应用背景模式。 */
enum class AppBackgroundMode(val prefValue: String) {
    Artwork("artwork"),
    Acrylic("acrylic"),
    SoftColor("soft_color");

    companion object {
        private const val PREFS_NAME = "AppView"
        private const val PREF_KEY = "app_background_mode"

        fun fromPrefValue(value: String?): AppBackgroundMode =
            values().firstOrNull { it.prefValue == value } ?: Artwork

        fun read(context: Context): AppBackgroundMode =
            fromPrefValue(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(PREF_KEY, null)
            )

        fun write(context: Context, mode: AppBackgroundMode) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY, mode.prefValue)
                .apply()
        }
    }
}
