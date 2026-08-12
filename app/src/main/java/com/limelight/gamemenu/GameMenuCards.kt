package com.limelight.gamemenu

// In-stream tuning cards and their shared slider/focus behavior.

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.limelight.CustomKeyData
import com.limelight.R
import com.limelight.binding.audio.AudioVibrationService
import com.limelight.ui.theme.AppShapes
import java.util.Locale


@Composable
internal fun GameMenuCards(
    state: GameMenuComposeUiState,
    callbacks: GameMenuCallbacks,
    modifier: Modifier = Modifier,
    onSliderGesture: (Boolean) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GameMenuDimens.tight)
    ) {
        if (state.visibleCards.bitrate) {
            BitrateCard(state.bitrate, callbacks, onSliderGesture, callbacks.onEditCards)
        }
        if (state.visibleCards.audioHaptics) {
            AudioHapticsCard(
                state.audioHaptics,
                callbacks,
                onSliderGesture,
                callbacks.onEditCards
            )
        }
        if (state.visibleCards.gyro) {
            GyroCard(state.gyro, callbacks, onSliderGesture, callbacks.onEditCards)
        }
        if (state.visibleCards.shortcuts && state.customKeys.isNotEmpty()) {
            ShortcutCard(state.customKeys, callbacks.onCustomKey, callbacks.onEditCards)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GameMenuCard(
    title: String,
    status: String? = null,
    titleAccessory: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val view = LocalView.current
    val longClickModifier = if (onLongClick != null) {
        Modifier
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                }
            )
    } else {
        Modifier
    }
    Surface(
        color = colorResource(R.color.game_menu_card_background),
        shape = GameMenuCardShape,
        border = BorderStroke(GameMenuDimens.surfaceStroke, colorResource(R.color.game_menu_button_border)),
        modifier = modifier
            .fillMaxWidth()
            // Keep long-press configuration on the card without making the whole
            // card consume a controller focus slot before its child controls.
            .focusProperties { canFocus = false }
            .then(longClickModifier)
    ) {
        Column(
            modifier = Modifier.padding(GameMenuDimens.section),
            verticalArrangement = Arrangement.spacedBy(GameMenuDimens.tight)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = colorResource(R.color.game_menu_text_primary),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    titleAccessory?.let {
                        Spacer(Modifier.width(GameMenuDimens.tight))
                        it()
                    }
                }
                Spacer(Modifier.weight(1f))
                if (trailing != null) {
                    trailing()
                } else status?.let {
                    Text(
                        text = it,
                        color = colorResource(R.color.game_menu_accent),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactGameMenuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val colors = SliderDefaults.colors()
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                thumbSize = GameMenuSliderSpec.thumbSize
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(GameMenuSliderSpec.trackHeight),
                colors = colors,
                thumbTrackGapSize = GameMenuSliderSpec.thumbTrackGap,
                trackInsideCornerSize = GameMenuSliderSpec.trackInsideCorner
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BitrateCard(
    state: BitrateCardState,
    callbacks: GameMenuCallbacks,
    onSliderGesture: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    val view = LocalView.current
    var tipVisible by remember { mutableStateOf(false) }
    val currentLabel = stringResource(
        R.string.game_menu_bitrate_current,
        state.currentBitrateKbps / 1000
    )
    GameMenuCard(
        title = stringResource(R.string.game_menu_tab_bitrate),
        status = state.abrStatus,
        titleAccessory = {
            BitrateHelpButton(
                tipVisible = tipVisible,
                onToggleTip = { tipVisible = !tipVisible },
                onDismissTip = { tipVisible = false },
                onLongClick = callbacks.onBitrateHapticMode
            )
        },
        onLongClick = onConfigure
    ) {
        Text(
            text = currentLabel,
            color = colorResource(R.color.game_menu_text_secondary),
            fontSize = 10.sp
        )
        Text(
            text = BitrateCardController.formatBitrateMbps(state.selectedBitrateKbps),
            color = colorResource(R.color.game_menu_accent),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        CompactGameMenuSlider(
            value = state.progress,
            onValueChange = { value ->
                if (callbacks.onBitrateProgress(value)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            },
            onValueChangeFinished = callbacks.onBitrateApply,
            valueRange = 0f..BitrateCardController.MAX_PROGRESS.toFloat(),
            modifier = Modifier
                .focusProperties { canFocus = true }
                .fillMaxWidth()
                .height(GameMenuSliderSpec.height)
                .gamepadFocusOutline(GameMenuControlShape)
                .handleSliderDpad(
                    value = state.progress,
                    step = 1f,
                    valueRange = 0f..BitrateCardController.MAX_PROGRESS.toFloat(),
                    onValueChange = { value ->
                        if (callbacks.onBitrateProgress(value)) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    onValueChangeFinished = callbacks.onBitrateApply
                )
                .lockParentScrollDuringGesture(onSliderGesture)
        )
        Row {
            Text("0.5 Mbps", color = colorResource(R.color.game_menu_text_secondary), fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("200 Mbps", color = colorResource(R.color.game_menu_text_secondary), fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BitrateHelpButton(
    tipVisible: Boolean,
    onToggleTip: () -> Unit,
    onDismissTip: () -> Unit,
    onLongClick: () -> Unit
) {
    val accent = colorResource(R.color.game_menu_accent)
    val helpDescription = stringResource(R.string.game_menu_bitrate_tip)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .focusProperties { canFocus = true }
            .gamepadFocusOutline(CircleShape)
            .semantics { contentDescription = helpDescription }
            .combinedClickable(
                onClick = onToggleTip,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            VisuallyCenteredBadgeText(
                text = "?",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (tipVisible) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = onDismissTip,
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    color = colorResource(R.color.game_menu_card_background),
                    shape = AppShapes.overlay,
                    border = BorderStroke(GameMenuDimens.surfaceStroke, accent.copy(alpha = 0.22f)),
                    modifier = Modifier.widthIn(max = 260.dp)
                ) {
                    Text(
                        text = stringResource(R.string.game_menu_bitrate_tip),
                        color = colorResource(R.color.game_menu_text_primary),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(GameMenuDimens.outer)
                    )
                }
            }
        }
    }
}

private data class AudioHapticsChoice(
    val value: String,
    val label: String,
    val selected: Boolean
)

@Composable
private fun AudioHapticsCard(
    state: AudioHapticsCardState,
    callbacks: GameMenuCallbacks,
    onSliderGesture: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    val view = LocalView.current
    val title = stringResource(R.string.game_menu_tab_audio_haptics)
    val modeNames = stringArrayResource(R.array.audio_vibration_mode_names)
    val modeValues = stringArrayResource(R.array.audio_vibration_mode_values)
    val sceneNames = stringArrayResource(R.array.audio_vibration_scene_names)
    val sceneValues = stringArrayResource(R.array.audio_vibration_scene_values)
    val modeChoices = modeValues.mapIndexed { index, value ->
        AudioHapticsChoice(
            value = value,
            label = when (value) {
                AudioVibrationService.MODE_DEVICE_ONLY ->
                    stringResource(R.string.game_menu_audio_haptics_route_device_short)
                AudioVibrationService.MODE_GAMEPAD_ONLY ->
                    stringResource(R.string.game_menu_audio_haptics_route_gamepad_short)
                AudioVibrationService.MODE_BOTH ->
                    stringResource(R.string.game_menu_audio_haptics_route_both_short)
                else -> modeNames.getOrElse(index) { value }
            },
            selected = value == state.mode
        )
    }
    val sceneChoices = sceneValues.mapIndexed { index, value ->
        AudioHapticsChoice(
            value = value,
            label = compactSegmentLabel(sceneNames.getOrElse(index) { value }),
            selected = value.toIntOrNull() == state.scene
        )
    }

    GameMenuCard(
        title = title,
        trailing = {
            AudioHapticsResetButton(callbacks.onAudioHapticsReset)
            Spacer(Modifier.width(GameMenuDimens.tight))
            InlineToggle(
                checked = state.enabled,
                contentDescription = title,
                onToggle = {
                    callbacks.onAudioHapticsEnabled(!state.enabled)
                }
            )
        },
        onLongClick = onConfigure
    ) {
        if (state.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.title_seekbar_audio_vibration_strength),
                    color = colorResource(R.color.game_menu_text_secondary),
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.game_menu_audio_haptics_strength_value,
                        state.strength
                    ),
                    color = colorResource(R.color.game_menu_accent),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            CompactGameMenuSlider(
                value = state.strength.toFloat(),
                onValueChange = { value ->
                    if (callbacks.onAudioHapticsStrength(value)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                },
                onValueChangeFinished = callbacks.onAudioHapticsStrengthFinished,
                valueRange = 0f..AudioVibrationService.MAX_STRENGTH.toFloat(),
                modifier = Modifier
                    .focusProperties { canFocus = true }
                    .fillMaxWidth()
                    .height(GameMenuSliderSpec.height)
                    .gamepadFocusOutline(GameMenuControlShape)
                    .handleSliderDpad(
                        value = state.strength.toFloat(),
                        step = AudioHapticsCardController.HAPTIC_STEP.toFloat(),
                        valueRange = 0f..AudioVibrationService.MAX_STRENGTH.toFloat(),
                        onValueChange = { value ->
                            if (callbacks.onAudioHapticsStrength(value)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                        onValueChangeFinished = callbacks.onAudioHapticsStrengthFinished
                    )
                    .lockParentScrollDuringGesture(onSliderGesture)
            )
            AudioHapticsChoiceRow(
                label = stringResource(R.string.title_list_audio_vibration_mode),
                choices = modeChoices,
                onChoice = callbacks.onAudioHapticsMode
            )
            AudioHapticsChoiceRow(
                label = stringResource(R.string.title_list_audio_vibration_scene),
                choices = sceneChoices,
                onChoice = { value ->
                    value.toIntOrNull()?.let(callbacks.onAudioHapticsScene)
                }
            )
        }
        if (state.pendingRestart) {
            Text(
                text = stringResource(R.string.game_menu_audio_haptics_pending_restart),
                color = colorResource(R.color.game_menu_accent),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AudioHapticsResetButton(onReset: () -> Unit) {
    val view = LocalView.current
    val description = stringResource(R.string.game_menu_audio_haptics_reset)
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .focusProperties { canFocus = true }
            .gamepadFocusOutline(shape)
            .semantics { contentDescription = description }
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onReset()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.phc_action_reset),
            contentDescription = null,
            tint = colorResource(R.color.game_menu_text_secondary),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AudioHapticsChoiceRow(
    label: String,
    choices: List<AudioHapticsChoice>,
    onChoice: (String) -> Unit
) {
    val view = LocalView.current
    val accent = colorResource(R.color.game_menu_accent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colorResource(R.color.game_menu_text_secondary),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.28f)
        )
        Row(
            modifier = Modifier
                .weight(0.72f)
                .fillMaxHeight()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            choices.forEach { choice ->
                val shape = AppShapes.small
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(
                            if (choice.selected) accent.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .focusProperties { canFocus = true }
                        .gamepadFocusOutline(shape)
                        .selectable(
                            selected = choice.selected,
                            role = Role.RadioButton,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onChoice(choice.value)
                            }
                        )
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = choice.label,
                        color = if (choice.selected) {
                            accent
                        } else {
                            colorResource(R.color.game_menu_text_secondary)
                        },
                        fontSize = 10.sp,
                        fontWeight = if (choice.selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GyroCard(
    state: GyroCardState,
    callbacks: GameMenuCallbacks,
    onSliderGesture: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    val title = stringResource(R.string.game_menu_tab_gyro)
    GameMenuCard(
        title = title,
        trailing = {
            Text(
                text = if (state.enabled) "ON" else "OFF",
                color = colorResource(R.color.game_menu_accent),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(GameMenuDimens.section))
            InlineToggle(
                checked = state.enabled,
                contentDescription = title,
                onToggle = {
                    callbacks.onGyroEnabled(!state.enabled)
                }
            )
        },
        onLongClick = onConfigure
    ) {
        if (state.enabled) {
            SettingSwitchRow(
                label = stringResource(R.string.gyro_mouse_mode_label),
                checked = state.mouseMode,
                onCheckedChange = callbacks.onGyroMouseMode
            )
            SettingValueRow(
                label = stringResource(R.string.gyro_activation_method),
                value = state.activationKeyLabel,
                onClick = callbacks.onGyroActivationKey
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gyro_sensitivity),
                    color = colorResource(R.color.game_menu_text_secondary),
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format(Locale.US, "%.1fx", state.sensitivity),
                    color = colorResource(R.color.game_menu_accent),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = state.sensitivity,
                onValueChange = callbacks.onGyroSensitivity,
                onValueChangeFinished = callbacks.onGyroSensitivityFinished,
                valueRange = 0.5f..10.0f,
                modifier = Modifier
                    .focusProperties { canFocus = true }
                    .fillMaxWidth()
                    .gamepadFocusOutline(GameMenuControlShape)
                    .handleSliderDpad(
                        value = state.sensitivity,
                        step = 0.1f,
                        valueRange = 0.5f..10.0f,
                        onValueChange = callbacks.onGyroSensitivity,
                        onValueChangeFinished = callbacks.onGyroSensitivityFinished
                    )
                    .lockParentScrollDuringGesture(onSliderGesture)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GameMenuDimens.outer)) {
                SettingSwitchRow(
                    label = stringResource(R.string.gyro_invert_x_axis),
                    checked = state.invertX,
                    onCheckedChange = callbacks.onGyroInvertX,
                    modifier = Modifier.weight(1f)
                )
                SettingSwitchRow(
                    label = stringResource(R.string.gyro_invert_y_axis),
                    checked = state.invertY,
                    onCheckedChange = callbacks.onGyroInvertY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun Modifier.lockParentScrollDuringGesture(
    onGestureActive: (Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onGestureActive(true)
        try {
            waitForUpOrCancellation()
        } finally {
            onGestureActive(false)
        }
    }
}

@Composable
internal fun Modifier.gamepadFocusOutline(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val focusColor = colorResource(R.color.game_menu_accent)
    return onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, focusColor, shape) else Modifier)
}

private fun Modifier.handleSliderDpad(
    value: Float,
    step: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    val direction = when (event.key) {
        Key.DirectionLeft -> -1f
        Key.DirectionRight -> 1f
        else -> return@onPreviewKeyEvent false
    }
    when (event.type) {
        KeyEventType.KeyDown -> {
            val adjusted = (value + direction * step).coerceIn(valueRange.start, valueRange.endInclusive)
            if (adjusted != value) onValueChange(adjusted)
            true
        }
        KeyEventType.KeyUp -> {
            onValueChangeFinished()
            true
        }
        else -> false
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = colorResource(R.color.game_menu_text_primary),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        InlineToggle(
            checked = checked,
            contentDescription = label,
            onToggle = {
                onCheckedChange(!checked)
            }
        )
    }
}

@Composable
private fun SettingValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = true }
            .clip(GameMenuControlShape)
            .gamepadFocusOutline(GameMenuControlShape)
            .clickable(onClick = onClick)
            .padding(vertical = GameMenuDimens.tight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colorResource(R.color.game_menu_text_secondary), fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = colorResource(R.color.game_menu_accent), fontSize = 10.sp)
        Spacer(Modifier.width(GameMenuDimens.tight))
        Text("›", color = colorResource(R.color.game_menu_text_secondary), fontSize = 14.sp)
    }
}

@Composable
private fun ShortcutCard(
    keys: List<CustomKeyData>,
    onKey: (CustomKeyData) -> Unit,
    onConfigure: () -> Unit
) {
    val view = LocalView.current
    GameMenuCard(
        title = stringResource(R.string.game_menu_tab_shortcuts),
        onLongClick = onConfigure
    ) {
        keys.forEachIndexed { index, key ->
            Text(
                text = key.name,
                color = colorResource(R.color.game_menu_text_primary),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = true }
                    .clip(GameMenuControlShape)
                    .gamepadFocusOutline(GameMenuControlShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey(key)
                    }
                    .padding(
                        horizontal = GameMenuDimens.section,
                        vertical = GameMenuDimens.compact
                    )
            )
            if (index < keys.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colorResource(R.color.game_menu_button_border))
                )
            }
        }
    }
}
