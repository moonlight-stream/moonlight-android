package com.limelight

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

private val GameMenuDialogShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val GameMenuCardShape = RoundedCornerShape(10.dp)
private val GameMenuControlShape = RoundedCornerShape(10.dp)
private const val GAME_MENU_MAX_HEIGHT_FRACTION = 0.90f
private const val GAME_MENU_WIDE_LAYOUT_MIN_WIDTH_DP = 576
private const val ORIENTATION_MISMATCH_THRESHOLD = 1.5f

private object GameMenuDimens {
    val surfaceStroke = 0.75.dp
    val tight = 4.dp
    val compact = 6.dp
    val section = 8.dp
    val outer = 12.dp
    val compactScreenInset = 16.dp
    val wideScreenInset = 28.dp
}

private object GameMenuFabricSpec {
    val spacing = 6.dp
    val strokeWidth = 0.30.dp
    const val SHADOW_OFFSET_FRACTION = 0.36f
}

private data class GameMenuPalette(
    val accent: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val dialogBackground: Color,
    val dialogBorder: Color,
    val darkTheme: Boolean
)

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
    val quickActions: List<GameMenuQuickAction>,
    val visibleCards: GameMenuVisibleCards,
    val bitrate: BitrateCardState,
    val gyro: GyroCardState,
    val customKeys: List<CustomKeyData>,
    val quickEditMode: Boolean = false,
    val isSubmenu: Boolean = false
)

internal data class GameMenuVisibleCards(
    val bitrate: Boolean,
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
    val onQuickAction: (String) -> Unit,
    val onToggleQuickEdit: () -> Unit,
    val onAddQuickAction: () -> Unit,
    val onRemoveQuickAction: (String) -> Unit,
    val onMoveQuickAction: (String, String) -> Unit,
    val onEditCards: () -> Unit,
    val onBitrateProgress: (Float) -> Boolean,
    val onBitrateApply: () -> Unit,
    val onBitrateHapticMode: () -> Unit,
    val onGyroEnabled: (Boolean) -> Unit,
    val onGyroMouseMode: (Boolean) -> Unit,
    val onGyroActivationKey: () -> Unit,
    val onGyroSensitivity: (Float) -> Unit,
    val onGyroSensitivityFinished: () -> Unit,
    val onGyroInvertX: (Boolean) -> Unit,
    val onGyroInvertY: (Boolean) -> Unit,
    val onCustomKey: (CustomKeyData) -> Unit
)

@Composable
internal fun GameMenuScreen(
    state: GameMenuComposeUiState,
    callbacks: GameMenuCallbacks
) {
    val palette = gameMenuPalette()
    val configuration = LocalConfiguration.current
    val maxMenuHeight = rememberGameMenuMaxHeight()
    val wideLayout = configuration.screenWidthDp >= GAME_MENU_WIDE_LAYOUT_MIN_WIDTH_DP
    val horizontalInset = if (wideLayout) {
        GameMenuDimens.wideScreenInset
    } else {
        GameMenuDimens.compactScreenInset
    }

    GameMenuTheme(palette) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalInset)
        ) {
            Surface(
                color = Color.Transparent,
                shape = GameMenuDialogShape,
                border = BorderStroke(GameMenuDimens.surfaceStroke, palette.dialogBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxMenuHeight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .gameMenuFabricBackground(
                            baseColor = palette.dialogBackground,
                            darkTheme = palette.darkTheme
                        )
                ) {
                    GameMenuContent(
                        state = state,
                        callbacks = callbacks,
                        wideLayout = wideLayout && !state.isSubmenu
                    )
                }
            }
        }
    }
}

@Composable
private fun gameMenuPalette() = GameMenuPalette(
    accent = colorResource(R.color.game_menu_accent),
    card = colorResource(R.color.game_menu_card_background),
    textPrimary = colorResource(R.color.game_menu_text_primary),
    textSecondary = colorResource(R.color.game_menu_text_secondary),
    dialogBackground = colorResource(R.color.game_menu_dialog_background),
    dialogBorder = colorResource(R.color.game_menu_dialog_border),
    darkTheme = isSystemInDarkTheme()
)

@Composable
private fun GameMenuTheme(
    palette: GameMenuPalette,
    content: @Composable () -> Unit
) {
    val baseColorScheme = if (palette.darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = baseColorScheme.copy(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accent.copy(alpha = 0.12f),
            onPrimaryContainer = palette.accent,
            secondary = palette.accent,
            onSecondary = Color.White,
            secondaryContainer = palette.accent.copy(alpha = 0.16f),
            onSecondaryContainer = palette.accent,
            tertiary = palette.accent,
            onTertiary = Color.White,
            tertiaryContainer = palette.accent.copy(alpha = 0.12f),
            onTertiaryContainer = palette.accent,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.card,
            surfaceContainerHighest = palette.accent.copy(alpha = 0.10f),
            onSurfaceVariant = palette.textSecondary,
            outline = palette.dialogBorder
        ),
        content = content
    )
}

@Composable
private fun GameMenuContent(
    state: GameMenuComposeUiState,
    callbacks: GameMenuCallbacks,
    wideLayout: Boolean
) {
    var sliderGestureActive by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()

    LaunchedEffect(state.title, state.isSubmenu) {
        menuScrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .verticalScroll(
                state = menuScrollState,
                enabled = !sliderGestureActive
            )
            .padding(GameMenuDimens.outer),
        verticalArrangement = Arrangement.spacedBy(GameMenuDimens.section)
    ) {
        GameMenuHeader(state, callbacks.onBack, callbacks.onCrownToggle)

        if (!state.isSubmenu) {
            QuickActionRow(
                actions = state.quickActions,
                superOptions = state.superOptions,
                editMode = state.quickEditMode,
                onAction = callbacks.onQuickAction,
                onSuperOptionClick = callbacks.onOptionClick,
                onToggleEdit = callbacks.onToggleQuickEdit,
                onAdd = callbacks.onAddQuickAction,
                onRemove = callbacks.onRemoveQuickAction,
                onMove = callbacks.onMoveQuickAction
            )
        }

        if (wideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GameMenuDimens.section),
                verticalAlignment = Alignment.Top
            ) {
                MenuOptionColumn(
                    options = state.options,
                    iconForOption = callbacks.iconForOption,
                    onOptionClick = callbacks.onOptionClick,
                    onInlineToggle = callbacks.onInlineToggle,
                    onSegmentClick = callbacks.onSegmentClick,
                    modifier = Modifier.weight(1f)
                )
                GameMenuCards(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.weight(1f),
                    onSliderGesture = { sliderGestureActive = it }
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GameMenuDimens.section)) {
                MenuOptionColumn(
                    state.options,
                    callbacks.iconForOption,
                    callbacks.onOptionClick,
                    callbacks.onInlineToggle,
                    callbacks.onSegmentClick
                )
                if (!state.isSubmenu) {
                    GameMenuCards(state, callbacks) { sliderGestureActive = it }
                }
            }
        }

        if (!state.isSubmenu && state.appName.isNotBlank()) {
            GameMenuFooter(state.appName)
        }
    }
}

@Composable
private fun rememberGameMenuMaxHeight(): Dp {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val view = LocalView.current
    var availableHeightPx by remember(configuration.orientation) {
        mutableStateOf(currentWindowContentHeightPx(context, view, configuration.orientation))
    }

    LaunchedEffect(configuration.orientation, view) {
        view.post {
            val laidOutHeight = view.rootView.height
            if (laidOutHeight > 0 &&
                availableHeightPx > laidOutHeight * ORIENTATION_MISMATCH_THRESHOLD
            ) {
                availableHeightPx = laidOutHeight
            }
        }
    }

    return with(LocalDensity.current) {
        (availableHeightPx * GAME_MENU_MAX_HEIGHT_FRACTION).toDp()
    }
}

private fun Modifier.gameMenuFabricBackground(
    baseColor: Color,
    darkTheme: Boolean
): Modifier = drawWithCache {
    val weaveSpacing = GameMenuFabricSpec.spacing.toPx()
    val weaveStroke = GameMenuFabricSpec.strokeWidth.toPx()
    val sheen = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (darkTheme) 0.018f else 0.06f),
            Color.Transparent,
            Color.Black.copy(alpha = if (darkTheme) 0.032f else 0.014f)
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    val lightThread = Color.White.copy(alpha = if (darkTheme) 0.012f else 0.028f)
    val shadowThread = Color.Black.copy(alpha = if (darkTheme) 0.018f else 0.010f)
    val crossThread = Color.White.copy(alpha = if (darkTheme) 0.008f else 0.016f)
    val lightThreadPath = Path()
    val shadowThreadPath = Path()
    val crossThreadPath = Path()
    val threadStyle = Stroke(width = weaveStroke)

    var threadX = -size.height
    while (threadX < size.width) {
        lightThreadPath.moveTo(threadX, 0f)
        lightThreadPath.lineTo(threadX + size.height, size.height)

        val shadowX = threadX + weaveSpacing * GameMenuFabricSpec.SHADOW_OFFSET_FRACTION
        shadowThreadPath.moveTo(shadowX, 0f)
        shadowThreadPath.lineTo(shadowX + size.height, size.height)
        threadX += weaveSpacing
    }

    threadX = 0f
    while (threadX < size.width + size.height) {
        crossThreadPath.moveTo(threadX, 0f)
        crossThreadPath.lineTo(threadX - size.height, size.height)
        threadX += weaveSpacing
    }

    onDrawBehind {
        drawRect(baseColor)
        drawRect(sheen)
        drawPath(lightThreadPath, lightThread, style = threadStyle)
        drawPath(shadowThreadPath, shadowThread, style = threadStyle)
        drawPath(crossThreadPath, crossThread, style = threadStyle)
    }
}

private fun currentWindowContentHeightPx(
    context: Context,
    view: View,
    orientation: Int
): Int {
    @Suppress("DEPRECATION")
    val display = view.display
        ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    val displayHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val mode = display.mode
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mode.physicalWidth
        } else {
            mode.physicalHeight
        }
    } else {
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        size.y
    }
    val insets = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
    return (displayHeight - (insets?.top ?: 0) - (insets?.bottom ?: 0)).coerceAtLeast(1)
}

@Composable
private fun GameMenuHeader(
    state: GameMenuComposeUiState,
    onBack: () -> Unit,
    onCrownToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isSubmenu) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24),
                contentDescription = stringResource(R.string.addpc_back),
                tint = colorResource(R.color.game_menu_text_primary),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .gamepadFocusOutline(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )
            Spacer(Modifier.width(GameMenuDimens.tight))
        }
        Text(
            text = state.title,
            color = colorResource(R.color.game_menu_text_primary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!state.isSubmenu) {
            val crownShape = CircleShape
            Icon(
                painter = painterResource(R.drawable.ic_super_crown),
                contentDescription = state.crownToggleText,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(36.dp)
                    .clip(crownShape)
                    .background(colorResource(R.color.game_menu_accent).copy(alpha = 0.10f))
                    .border(
                        GameMenuDimens.surfaceStroke,
                        colorResource(R.color.game_menu_accent).copy(alpha = 0.20f),
                        crownShape
                    )
                    .gamepadFocusOutline(crownShape)
                    .clickable(onClick = onCrownToggle)
                    .padding(7.dp)
            )
        }
    }
}

@Composable
private fun GameMenuFooter(subtitle: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = subtitle,
            color = colorResource(R.color.game_menu_text_secondary),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionRow(
    actions: List<GameMenuQuickAction>,
    superOptions: List<GameMenu.MenuOption>,
    editMode: Boolean,
    onAction: (String) -> Unit,
    onSuperOptionClick: (GameMenu.MenuOption) -> Unit,
    onToggleEdit: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, String) -> Unit
) {
    val view = LocalView.current
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }

    fun finishDrag(commit: Boolean) {
        val sourceId = draggingId
        val targetId = dropTargetId
        draggingId = null
        dragOffset = Offset.Zero
        dropTargetId = null
        if (commit && sourceId != null && targetId != null && sourceId != targetId) {
            onMove(sourceId, targetId)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GameMenuDimens.tight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                key(action.id) {
                    val isDragging = draggingId == action.id
                    val isDropTarget = dropTargetId == action.id
                    val itemModifier = if (editMode) {
                        Modifier
                            .onGloballyPositioned { coordinates ->
                                itemBounds[action.id] = coordinates.boundsInParent()
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    alpha = 0.42f
                                }
                                if (isDropTarget) {
                                    scaleX = 1.12f
                                    scaleY = 1.12f
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val index = actions.indexOfFirst { it.id == action.id }
                                val targetIndex = when (event.key) {
                                    Key.DirectionLeft -> index - 1
                                    Key.DirectionRight -> index + 1
                                    else -> return@onPreviewKeyEvent false
                                }
                                actions.getOrNull(targetIndex)?.let { onMove(action.id, it.id) } != null
                            }
                    } else {
                        Modifier
                    }
                    val dragHandleModifier = if (editMode) {
                        Modifier.pointerInput(action.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                down.consume()
                                draggingId = action.id
                                dragOffset = Offset.Zero
                                dropTargetId = null
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                                var completed = false
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                        .firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        finishDrag(commit = true)
                                        completed = true
                                        break
                                    }

                                    val amount = change.positionChange()
                                    change.consume()
                                    dragOffset += amount
                                    val sourceBounds = itemBounds[action.id]
                                    val draggedCenter = sourceBounds?.center?.plus(dragOffset)
                                    dropTargetId = draggedCenter?.let { center ->
                                        actions.firstOrNull { candidate ->
                                            candidate.id != action.id &&
                                                itemBounds[candidate.id]?.contains(center) == true
                                        }?.id
                                    }
                                }
                                if (!completed) {
                                    finishDrag(commit = false)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                    QuickActionChip(
                        action = action,
                        editMode = editMode,
                        onClick = { onAction(action.id) },
                        onEnterEdit = onToggleEdit,
                        onRemove = { onRemove(action.id) },
                        modifier = itemModifier,
                        dragHandleModifier = dragHandleModifier
                    )
                }
            }
            if (!editMode) {
                superOptions.forEach { option ->
                    SuperOptionChip(option) { onSuperOptionClick(option) }
                }
            }
        }
        if (editMode) {
            Spacer(Modifier.width(GameMenuDimens.tight))
            Row(horizontalArrangement = Arrangement.spacedBy(GameMenuDimens.tight)) {
                ToolIconButton(
                    iconRes = R.drawable.phc_action_check,
                    contentDescription = stringResource(R.string.quick_action_done),
                    onClick = onToggleEdit
                )
                ToolIconButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = stringResource(R.string.quick_action_edit),
                    onClick = onAdd
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    action: GameMenuQuickAction,
    editMode: Boolean,
    onClick: () -> Unit,
    onEnterEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val view = LocalView.current
    val contentAlpha = if (action.enabled) 1f else 0.45f
    ActionPill(
        backgroundColor = colorResource(R.color.game_menu_card_background).copy(alpha = contentAlpha),
        borderColor = colorResource(R.color.game_menu_button_border),
        onClick = if (editMode) null else onClick,
        onLongClick = if (editMode) null else {
            {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onEnterEdit()
            }
        },
        modifier = modifier
    ) {
        if (editMode) {
            QuickActionDragHandle(dragHandleModifier)
            Spacer(Modifier.width(GameMenuDimens.tight))
        }
        if (action.iconRes != 0) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                },
                update = { imageView ->
                    imageView.setImageResource(action.iconRes)
                    imageView.clearColorFilter()
                    imageView.imageTintList = null
                },
                modifier = Modifier.size(20.dp)
            )
        } else {
            ActionTextBadge(action.iconText ?: firstActionCharacter(action.label))
        }
        Spacer(Modifier.width(GameMenuDimens.compact))
        Text(
            text = compactActionLabel(action.label),
            color = colorResource(R.color.game_menu_text_primary).copy(alpha = contentAlpha),
            fontSize = 14.sp,
            maxLines = 1
        )
        if (editMode) {
            Spacer(Modifier.width(GameMenuDimens.tight))
            QuickActionRemoveButton(onRemove)
        }
    }
}

@Composable
private fun QuickActionDragHandle(modifier: Modifier = Modifier) {
    val accent = colorResource(R.color.game_menu_accent)
    Column(
        modifier = modifier
            .width(36.dp)
            .heightIn(min = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(2.dp)
                .background(accent.copy(alpha = 0.42f), CircleShape)
        )
        Box(
            Modifier
                .width(15.dp)
                .height(2.dp)
                .background(accent.copy(alpha = 0.68f), CircleShape)
        )
        Box(
            Modifier
                .width(10.dp)
                .height(2.dp)
                .background(accent.copy(alpha = 0.42f), CircleShape)
        )
    }
}

@Composable
private fun QuickActionRemoveButton(onRemove: () -> Unit) {
    val view = LocalView.current
    val deleteLabel = stringResource(R.string.dialog_button_delete)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .semantics { contentDescription = deleteLabel }
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onRemove()
            },
        contentAlignment = Alignment.Center
    ) {
        Text("×", color = colorResource(R.color.game_menu_danger_text), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToolIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(colorResource(R.color.game_menu_accent).copy(alpha = 0.08f))
            .border(GameMenuDimens.surfaceStroke, colorResource(R.color.game_menu_accent).copy(alpha = 0.20f), shape)
            .gamepadFocusOutline(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = colorResource(R.color.game_menu_accent),
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionPill(
    backgroundColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val view = LocalView.current
    val interactionModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            onClick = onClick?.let { click ->
                {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    click()
                }
            } ?: {},
            onLongClick = onLongClick
        )
        onClick != null -> Modifier.clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
        }
        else -> Modifier.focusable()
    }

    Row(
        modifier = modifier
            .widthIn(min = 88.dp)
            .heightIn(min = 40.dp)
            .clip(GameMenuControlShape)
            .background(backgroundColor)
            .border(GameMenuDimens.surfaceStroke, borderColor, GameMenuControlShape)
            .gamepadFocusOutline(GameMenuControlShape)
            .then(interactionModifier)
            .padding(horizontal = GameMenuDimens.outer),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun MenuOptionColumn(
    options: List<GameMenu.MenuOption>,
    iconForOption: (String?) -> Int,
    onOptionClick: (GameMenu.MenuOption) -> Unit,
    onInlineToggle: (GameMenu.InlineControl.Toggle) -> Unit,
    onSegmentClick: (GameMenu.SegmentOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GameMenuDimens.tight)) {
        options.forEach { option ->
            MenuOptionRow(
                option = option,
                iconRes = iconForOption(option.iconKey),
                onClick = { onOptionClick(option) },
                onInlineToggle = onInlineToggle,
                onSegmentClick = onSegmentClick
            )
        }
    }
}

@Composable
private fun MenuOptionRow(
    option: GameMenu.MenuOption,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onInlineToggle: (GameMenu.InlineControl.Toggle) -> Unit,
    onSegmentClick: (GameMenu.SegmentOption) -> Unit
) {
    val view = LocalView.current
    val shape = GameMenuCardShape
    val inlineControl = option.inlineControl
    val showChevronAfterTitle = option.showChevron && inlineControl != null
    val hasDedicatedToggleAction = inlineControl is GameMenu.InlineControl.Toggle &&
        inlineControl.toggleAction != null
    val danger = option.iconKey == "game_menu_disconnect" ||
        option.iconKey == "game_menu_disconnect_and_quit"
    val borderColor = when {
        option.isCrownControl -> colorResource(R.color.game_menu_accent).copy(alpha = 0.55f)
        else -> colorResource(R.color.game_menu_list_item_border)
    }
    val activate = {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
    val rowInteraction = when {
        inlineControl is GameMenu.InlineControl.Toggle && !hasDedicatedToggleAction -> Modifier
            .gamepadFocusOutline(shape)
            .toggleable(
                value = inlineControl.checked,
                role = Role.Switch,
                onValueChange = { activate() }
            )
        inlineControl !is GameMenu.InlineControl.Segmented && option.runnable != null -> Modifier
            .gamepadFocusOutline(shape)
            .clickable(onClick = activate)
        else -> Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(colorResource(R.color.game_menu_list_item_normal))
            .border(GameMenuDimens.surfaceStroke, borderColor, shape)
            .then(rowInteraction)
            .padding(horizontal = GameMenuDimens.section),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (option.isShowIcon && iconRes != 0) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(GameMenuDimens.section))
        }

        val labelModifier = if (
            inlineControl is GameMenu.InlineControl.Segmented && option.runnable != null
        ) {
            Modifier
                .gamepadFocusOutline(GameMenuControlShape)
                .clickable(onClick = activate)
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .weight(if (inlineControl is GameMenu.InlineControl.Segmented) 0.42f else 1f)
                .then(labelModifier)
                .padding(vertical = GameMenuDimens.compact)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.label,
                    color = if (danger) {
                        colorResource(R.color.game_menu_danger_text)
                    } else {
                        colorResource(R.color.game_menu_text_primary)
                    },
                    fontSize = 13.sp,
                    fontWeight = if (option.isCrownControl) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (showChevronAfterTitle) {
                        Modifier.weight(1f, fill = false)
                    } else {
                        Modifier
                    }
                )
                if (showChevronAfterTitle) {
                    Spacer(Modifier.width(GameMenuDimens.tight))
                    MenuChevron(size = 12.dp)
                }
            }
            option.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = colorResource(R.color.game_menu_text_secondary),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when (inlineControl) {
            is GameMenu.InlineControl.Toggle -> {
                InlineToggle(
                    checked = inlineControl.checked,
                    contentDescription = option.label,
                    onToggle = if (hasDedicatedToggleAction) {
                        {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onInlineToggle(inlineControl)
                        }
                    } else {
                        null
                    }
                )
            }
            is GameMenu.InlineControl.Segmented -> {
                Spacer(Modifier.width(GameMenuDimens.section))
                InlineSegmentedControl(
                    segments = inlineControl.segments,
                    onSegmentClick = onSegmentClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                )
            }
            null -> if (option.showChevron) MenuChevron()
        }
    }
}

@Composable
private fun MenuChevron(size: Dp = 13.dp) {
    Icon(
        painter = painterResource(R.drawable.ic_arrow_right),
        contentDescription = null,
        tint = colorResource(R.color.game_menu_text_secondary),
        modifier = Modifier.size(size)
    )
}

@Composable
private fun InlineToggle(
    checked: Boolean,
    contentDescription: String,
    onToggle: (() -> Unit)? = null
) {
    val accent = colorResource(R.color.game_menu_accent)
    val track = if (checked) accent.copy(alpha = 0.82f) else colorResource(R.color.game_menu_button_border)
    val outline = if (checked) accent.copy(alpha = 0.46f) else colorResource(R.color.game_menu_list_item_border)
    val interactionModifier = if (onToggle != null) {
        Modifier
            .semantics { this.contentDescription = contentDescription }
            .gamepadFocusOutline(CircleShape)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(36.dp)
            .then(interactionModifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(track)
                .border(GameMenuDimens.surfaceStroke, outline, CircleShape)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        GameMenuDimens.surfaceStroke,
                        if (checked) accent.copy(alpha = 0.28f) else Color.White,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun InlineSegmentedControl(
    segments: List<GameMenu.SegmentOption>,
    onSegmentClick: (GameMenu.SegmentOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val accent = colorResource(R.color.game_menu_accent)
    Row(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEach { segment ->
            val segmentShape = RoundedCornerShape(7.dp)
            val background = if (segment.selected) {
                accent.copy(alpha = 0.12f)
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(segmentShape)
                    .background(background)
                    .gamepadFocusOutline(segmentShape)
                    .selectable(
                        selected = segment.selected,
                        role = Role.RadioButton,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSegmentClick(segment)
                        }
                    )
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = compactSegmentLabel(segment.label),
                    color = if (segment.selected) accent else colorResource(R.color.game_menu_text_secondary),
                    fontSize = 10.sp,
                    fontWeight = if (segment.selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun compactSegmentLabel(label: String): String {
    val text = label.trim()
    if (' ' in text) return compactActionLabel(text.substringBefore(' '), maxCodePoints = 8)

    val codePointCount = text.codePointCount(0, text.length)
    if (!text.containsHanCodePoint() || codePointCount <= 4) {
        return compactActionLabel(text, maxCodePoints = 8)
    }
    val prefixEnd = text.offsetByCodePoints(0, 2)
    val suffixStart = text.offsetByCodePoints(0, codePointCount - 2)
    return text.substring(0, prefixEnd) + text.substring(suffixStart)
}

private fun String.containsHanCodePoint(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        if (codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2EBEF
        ) {
            return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}

@Composable
private fun ActionTextBadge(text: String) {
    val accent = colorResource(R.color.game_menu_accent)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        VisuallyCenteredBadgeText(
            text = text,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VisuallyCenteredBadgeText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight
) {
    Layout(
        content = {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = fontWeight,
                maxLines = 1
            )
        }
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, -1)
        }
    }
}

@Composable
private fun SuperOptionChip(
    option: GameMenu.MenuOption,
    onClick: () -> Unit
) {
    val accent = colorResource(R.color.game_menu_accent)
    ActionPill(
        backgroundColor = accent.copy(alpha = 0.07f),
        borderColor = accent.copy(alpha = 0.20f),
        onClick = onClick
    ) {
        ActionTextBadge(firstActionCharacter(option.label))
        Spacer(Modifier.width(GameMenuDimens.compact))
        Text(
            text = compactActionLabel(option.label),
            color = colorResource(R.color.game_menu_text_primary),
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

private fun compactActionLabel(label: String, maxCodePoints: Int = 8): String {
    val text = label.trim()
    if (text.codePointCount(0, text.length) <= maxCodePoints) return text
    val visibleCodePoints = (maxCodePoints - 1).coerceAtLeast(0)
    val endIndex = text.offsetByCodePoints(0, visibleCodePoints)
    return text.substring(0, endIndex) + "…"
}

private fun firstActionCharacter(label: String): String {
    val text = label.trim()
    if (text.isEmpty()) return "?"
    return text.substring(0, text.offsetByCodePoints(0, 1))
}

@Composable
private fun GameMenuCards(
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
private fun GameMenuCard(
    title: String,
    status: String? = null,
    titleAccessory: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val view = LocalView.current
    val longClickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
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
        modifier = Modifier
            .fillMaxWidth()
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
        Slider(
            value = state.progress,
            onValueChange = { value ->
                if (callbacks.onBitrateProgress(value)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            },
            onValueChangeFinished = callbacks.onBitrateApply,
            valueRange = 0f..BitrateCardController.MAX_PROGRESS.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
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
                    shape = GameMenuCardShape,
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

@Composable
private fun GyroCard(
    state: GyroCardState,
    callbacks: GameMenuCallbacks,
    onSliderGesture: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    GameMenuCard(
        title = stringResource(R.string.game_menu_tab_gyro),
        trailing = {
            Text(
                text = if (state.enabled) "ON" else "OFF",
                color = colorResource(R.color.game_menu_accent),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(GameMenuDimens.section))
            Switch(
                checked = state.enabled,
                onCheckedChange = callbacks.onGyroEnabled,
                modifier = Modifier.gamepadFocusOutline(RoundedCornerShape(18.dp))
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
private fun Modifier.gamepadFocusOutline(shape: Shape): Modifier {
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.gamepadFocusOutline(RoundedCornerShape(18.dp))
        )
    }
}

@Composable
private fun SettingValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
