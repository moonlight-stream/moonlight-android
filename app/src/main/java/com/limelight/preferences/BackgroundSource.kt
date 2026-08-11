package com.limelight.preferences

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import java.io.File
import java.util.UUID

/**
 * Background image source model (issue #263).
 *
 * A single sealed hierarchy owns everything about "where does the home-screen
 * wallpaper come from": which pref values represent it, how to turn the source
 * into a Glide-loadable URL/path, and how to switch to it atomically.
 *
 * This replaces the earlier scheme where PcView held a handful of string
 * constants and four independent preference keys, and every call site that
 * changed the source had to remember to keep the keys consistent.
 */
sealed class BackgroundSource(val prefValue: String) {

    /**
     * Immutable identity of the wallpaper currently selected for display.
     * [cacheKey] distinguishes successive images from random APIs even when
     * their request URL is unchanged.
     */
    data class ResolvedTarget(
        val source: BackgroundSource,
        val target: String?,
        val cacheKey: String
    )

    /**
     * Resolve the target that Glide should load for this source.
     *
     * - Returns a non-empty string (HTTP URL or filesystem path) when a bitmap
     *   should be fetched.
     * - Returns `null` when no background should be shown (`None`, or a source
     *   that is mis-configured and should degrade silently).
     */
    abstract fun resolveTarget(ctx: Context, orientation: Int): String?

    /** Smart default: pick Picsum on TV/Leanback, Pipw elsewhere. */
    data object Auto : BackgroundSource("auto") {
        override fun resolveTarget(ctx: Context, orientation: Int): String? =
            if (isTvDevice(ctx)) Picsum.resolveTarget(ctx, orientation)
            else Pipw.resolveTarget(ctx, orientation)
    }

    /** Animé (Pipw API). Preserved for users who want the legacy look. */
    data object Pipw : BackgroundSource("pipw") {
        override fun resolveTarget(ctx: Context, orientation: Int): String =
            if (orientation == Configuration.ORIENTATION_PORTRAIT)
                "https://img-api.pipw.top/?phone=true"
            else
                "https://img-api.pipw.top"
    }

    /** Lorem Picsum photography. Unsplash-licensed, family/TV safe. */
    data object Picsum : BackgroundSource("picsum") {
        override fun resolveTarget(ctx: Context, orientation: Int): String {
            // Picsum returns the same image for the same URL; append a timestamp
            // so each request actually rotates.
            val ts = System.currentTimeMillis()
            return if (orientation == Configuration.ORIENTATION_PORTRAIT)
                "https://picsum.photos/1080/1920?random=$ts"
            else
                "https://picsum.photos/1920/1080?random=$ts"
        }
    }

    /** User-supplied API / image URL. Falls back to [Auto] if the pref is blank. */
    data object Api : BackgroundSource("api") {
        override fun resolveTarget(ctx: Context, orientation: Int): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val url = prefs.getString(KEY_API_URL, null)
            return if (!url.isNullOrEmpty()) url
            else Auto.resolveTarget(ctx, orientation)
        }
    }

    /** Local file. Falls back to [Auto] if the file is missing (and self-heals). */
    data object Local : BackgroundSource("local") {
        override fun resolveTarget(ctx: Context, orientation: Int): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val path = prefs.getString(KEY_LOCAL_PATH, null)
            if (path != null && File(path).exists()) return path
            // Self-heal: demote to Auto so user sees something.
            setActive(ctx, Auto)
            prefs.edit().remove(KEY_LOCAL_PATH).apply()
            return Auto.resolveTarget(ctx, orientation)
        }
    }

    /** No background at all. */
    data object None : BackgroundSource("none") {
        override fun resolveTarget(ctx: Context, orientation: Int): String? = null
    }

    companion object {
        const val KEY_SOURCE = "background_source"
        const val KEY_API_URL = "background_image_url"
        const val KEY_LOCAL_PATH = "background_image_local_path"
        /** First-run dialog idempotency flag. */
        const val KEY_DIALOG_SHOWN = "background_source_dialog_shown"

        /** Broadcast action consumed by PcView to trigger a reload. */
        const val ACTION_REFRESH = "com.limelight.REFRESH_BACKGROUND_IMAGE"

        // Legacy pref key from before the unified source refactor. Kept only
        // long enough to migrate existing installs, then erased.
        private const val LEGACY_KEY_TYPE = "background_image_type"

        private val ALL = listOf(Auto, Pipw, Picsum, Api, Local, None)
        private var cachedResolvedTarget: ResolvedTarget? = null
        private var resolvedTargetOrientation: Int? = null

        fun fromPrefValue(value: String?): BackgroundSource =
            ALL.firstOrNull { it.prefValue == value } ?: Auto

        /** Read the currently active source, running a one-shot legacy migration. */
        fun current(ctx: Context): BackgroundSource {
            migrateLegacyIfNeeded(ctx)
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            return fromPrefValue(prefs.getString(KEY_SOURCE, null))
        }

        @Synchronized
        fun resolveCurrentTarget(ctx: Context, orientation: Int): ResolvedTarget {
            val source = current(ctx)
            cachedResolvedTarget?.let { cached ->
                if (cached.source.prefValue == source.prefValue &&
                    resolvedTargetOrientation == orientation
                ) {
                    return cached
                }
            }

            val resolved = ResolvedTarget(
                source = source,
                target = source.resolveTarget(ctx, orientation),
                cacheKey = UUID.randomUUID().toString()
            )
            cachedResolvedTarget = resolved
            resolvedTargetOrientation = orientation
            return resolved
        }

        @Synchronized
        fun invalidateResolvedTarget() {
            cachedResolvedTarget = null
            resolvedTargetOrientation = null
        }

        /**
         * Atomically switch to [source], clear unrelated keys, mark the first-run
         * dialog as handled, and broadcast a refresh so live UI updates.
         *
         * Call sites never touch the individual prefs; this is the single writer.
         */
        fun setActive(ctx: Context, source: BackgroundSource) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val editor = prefs.edit()
                .putString(KEY_SOURCE, source.prefValue)
                .putBoolean(KEY_DIALOG_SHOWN, true)
                .remove(LEGACY_KEY_TYPE)
            // Forget state that belongs to the source we are leaving.
            if (source !is Api) editor.remove(KEY_API_URL)
            if (source !is Local) editor.remove(KEY_LOCAL_PATH)
            editor.apply()
            invalidateResolvedTarget()
            ctx.sendBroadcast(Intent(ACTION_REFRESH).setPackage(ctx.packageName))
        }

        /** Like [setActive] but keeps the URL/path (used by the picker prefs themselves). */
        fun setActivePreservingExtras(ctx: Context, source: BackgroundSource) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            prefs.edit()
                .putString(KEY_SOURCE, source.prefValue)
                .putBoolean(KEY_DIALOG_SHOWN, true)
                .remove(LEGACY_KEY_TYPE)
                .apply()
            invalidateResolvedTarget()
            ctx.sendBroadcast(Intent(ACTION_REFRESH).setPackage(ctx.packageName))
        }

        fun isDialogShown(ctx: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(KEY_DIALOG_SHOWN, false)

        fun markDialogShown(ctx: Context) {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit().putBoolean(KEY_DIALOG_SHOWN, true).apply()
        }

        fun isTvDevice(ctx: Context): Boolean {
            val uiModeManager = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                    ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
        }

        /**
         * One-shot migration from the pre-refactor scheme:
         *   background_image_type ∈ {"default","api","local"} → background_source
         * Runs at most once per install; afterward [LEGACY_KEY_TYPE] is removed.
         */
        private fun migrateLegacyIfNeeded(ctx: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            if (!prefs.contains(LEGACY_KEY_TYPE)) return
            if (prefs.contains(KEY_SOURCE)) {
                // New key already authoritative; just drop the legacy key.
                prefs.edit().remove(LEGACY_KEY_TYPE).apply()
                return
            }
            val migrated = when (prefs.getString(LEGACY_KEY_TYPE, null)) {
                "api" -> Api
                "local" -> Local
                else -> Auto
            }
            prefs.edit()
                .putString(KEY_SOURCE, migrated.prefValue)
                .remove(LEGACY_KEY_TYPE)
                .apply()
        }
    }
}
