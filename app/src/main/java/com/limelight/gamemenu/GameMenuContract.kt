package com.limelight.gamemenu

import androidx.annotation.DrawableRes
import com.limelight.CustomKeyData

internal data class GameMenuQuickAction(
    val id: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val iconText: String? = null,
    val enabled: Boolean = true
)

internal data class GameMenuComposeUiState(
    val title: String,
    val options: List<GameMenu.MenuOption>,
    val superOptions: List<GameMenu.MenuOption>,
    val appName: String,
    val crownToggleText: String,
    val deviceQuickOptions: List<GameMenu.MenuOption>,
    val quickActions: List<GameMenuQuickAction>,
    val visibleCards: GameMenuVisibleCards,
    val bitrate: BitrateCardState,
    val audioHaptics: AudioHapticsCardState,
    val gyro: GyroCardState,
    val customKeys: List<CustomKeyData>,
    val quickEditMode: Boolean = false,
    val isSubmenu: Boolean = false
)

internal data class GameMenuVisibleCards(
    val bitrate: Boolean,
    val audioHaptics: Boolean,
    val gyro: Boolean,
    val shortcuts: Boolean
)

internal data class GameMenuCallbacks(
    val iconForOption: (String?) -> Int,
    val onBack: () -> Unit,
    val onCrownToggle: () -> Unit,
    val onOptionClick: (GameMenu.MenuOption) -> Unit,
    val onInlineToggle: (GameMenu.InlineControl.Toggle) -> Unit,
    val onSegmentClick: (GameMenu.SegmentOption) -> Unit,
    val onEmptySuperCommandClick: () -> Unit,
    val onQuickAction: (String) -> Unit,
    val onToggleQuickEdit: () -> Unit,
    val onAddQuickAction: () -> Unit,
    val onRemoveQuickAction: (String) -> Unit,
    val onMoveQuickAction: (String, String) -> Unit,
    val onEditCards: () -> Unit,
    val onBitrateProgress: (Float) -> Boolean,
    val onBitrateApply: () -> Unit,
    val onBitrateHapticMode: () -> Unit,
    val onAudioHapticsEnabled: (Boolean) -> Unit,
    val onAudioHapticsStrength: (Float) -> Boolean,
    val onAudioHapticsStrengthFinished: () -> Unit,
    val onAudioHapticsMode: (String) -> Unit,
    val onAudioHapticsScene: (Int) -> Unit,
    val onAudioHapticsReset: () -> Unit,
    val onGyroEnabled: (Boolean) -> Unit,
    val onGyroMouseMode: (Boolean) -> Unit,
    val onGyroActivationKey: () -> Unit,
    val onGyroSensitivity: (Float) -> Unit,
    val onGyroSensitivityFinished: () -> Unit,
    val onGyroInvertX: (Boolean) -> Unit,
    val onGyroInvertY: (Boolean) -> Unit,
    val onCustomKey: (CustomKeyData) -> Unit
)
