package com.limelight.ui

import android.content.Context

/** A stable guide name plus an explicit content revision. */
data class FeatureGuideSpec(
    val name: String,
    val revision: Int
) {
    init {
        require(name.matches(Regex("[a-z0-9_]+"))) { "Guide names must use snake_case" }
        require(revision > 0) { "Guide revisions must be positive" }
    }

    val completionKey: String = "${name}_v$revision"
}

/** The single place to register and bump guides shipped by the app. */
object FeatureGuideRegistry {
    val PcViewDiscovery = FeatureGuideSpec("pcview_discovery", revision = 1)
    val AppViewDiscovery = FeatureGuideSpec("appview_discovery", revision = 1)
    val GameMenuDiscovery = FeatureGuideSpec("game_menu_discovery", revision = 1)
}

internal interface FeatureGuidePreferences {
    fun getBoolean(key: String): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** Shared completion semantics for both View and Compose guide presenters. */
class FeatureGuideStore internal constructor(
    private val preferences: FeatureGuidePreferences
) {
    constructor(context: Context) : this(
        object : FeatureGuidePreferences {
            private val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            override fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

            override fun putBoolean(key: String, value: Boolean) {
                prefs.edit().putBoolean(key, value).apply()
            }
        }
    )

    fun shouldShow(spec: FeatureGuideSpec): Boolean =
        !preferences.getBoolean(spec.completionKey)

    fun markCompleted(spec: FeatureGuideSpec) {
        preferences.putBoolean(spec.completionKey, true)
    }

    companion object {
        private const val PREFS_NAME = "feature_guides"
    }
}
