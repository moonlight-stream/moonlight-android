package com.limelight.gamemenu

import android.content.Context
import com.limelight.R
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.AppActionSheet

/** Shared card-visibility editor used by both Settings and the in-stream menu. */
internal object GameMenuCardVisibilityEditor {
    private const val BITRATE = 0
    private const val AUDIO_HAPTICS = 1
    private const val GYRO = 2
    private const val SHORTCUTS = 3

    fun show(
        context: Context,
        config: PreferenceConfiguration,
        onSaved: (PreferenceConfiguration) -> Unit
    ) {
        val selected = selectedIds(config)
        AppActionSheet.showMultiSelect(
            context = context,
            title = context.getString(R.string.game_menu_card_config_title),
            actions = labels(context).mapIndexed { index, label ->
                AppActionSheet.Action(index, label, checked = index in selected)
            },
            confirmLabel = context.getString(R.string.game_menu_ok).trim(),
            cancelLabel = context.getString(R.string.game_menu_cancel).trim(),
            minimumSelectionCount = 1,
            onConfirm = { selectedIds ->
                config.showBitrateCard = BITRATE in selectedIds
                config.showAudioHapticsCard = AUDIO_HAPTICS in selectedIds
                config.showGyroCard = GYRO in selectedIds
                config.showQuickKeyCard = SHORTCUTS in selectedIds
                config.writePreferences(context)
                onSaved(config)
            }
        )
    }

    fun selectedLabels(context: Context, config: PreferenceConfiguration): List<String> {
        val labels = labels(context)
        return selectedIds(config).sorted().map(labels::get)
    }

    private fun labels(context: Context): List<String> = listOf(
        context.getString(R.string.game_menu_tab_bitrate),
        context.getString(R.string.game_menu_tab_audio_haptics),
        context.getString(R.string.game_menu_tab_gyro),
        context.getString(R.string.game_menu_tab_shortcuts)
    )

    private fun selectedIds(config: PreferenceConfiguration): Set<Int> = setOfNotNull(
        BITRATE.takeIf { config.showBitrateCard },
        AUDIO_HAPTICS.takeIf { config.showAudioHapticsCard },
        GYRO.takeIf { config.showGyroCard },
        SHORTCUTS.takeIf { config.showQuickKeyCard }
    )
}
