package com.limelight.gamemenu

// Quick-action presentation, editing, and drag-and-drop behavior.

import android.view.HapticFeedbackConstants
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.limelight.R


@Composable
internal fun QuickActionRow(
    actions: List<GameMenuQuickAction>,
    superOptions: List<GameMenu.MenuOption>,
    editMode: Boolean,
    onAction: (String) -> Unit,
    onSuperOptionClick: (GameMenu.MenuOption) -> Unit,
    onEmptySuperCommandClick: () -> Unit,
    onToggleEdit: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, String) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth(),
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
                if (superOptions.isEmpty()) {
                    EmptySuperCommandChip(onClick = onEmptySuperCommandClick)
                } else {
                    superOptions.forEach { option ->
                        SuperOptionChip(option) { onSuperOptionClick(option) }
                    }
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
internal fun ActionPill(
    backgroundColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    dashedBorder: Boolean = false,
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
    val borderModifier = if (dashedBorder) {
        Modifier.dashedPillBorder(borderColor)
    } else {
        Modifier.border(GameMenuDimens.surfaceStroke, borderColor, GameMenuControlShape)
    }

    Row(
        modifier = modifier
            .widthIn(min = 88.dp)
            .heightIn(min = 40.dp)
            .clip(GameMenuControlShape)
            .background(backgroundColor)
            .then(borderModifier)
            .gamepadFocusOutline(GameMenuControlShape)
            .then(interactionModifier)
            .padding(horizontal = GameMenuDimens.outer),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

private fun Modifier.dashedPillBorder(color: Color): Modifier = drawWithCache {
    val strokeWidth = GameMenuDimens.surfaceStroke.toPx()
    val inset = strokeWidth / 2f
    val cornerRadius = (GameMenuControlRadius.toPx() - inset).coerceAtLeast(0f)
    val pathEffect = PathEffect.dashPathEffect(
        intervals = floatArrayOf(6.dp.toPx(), 4.dp.toPx())
    )
    onDrawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(
                width = (size.width - strokeWidth).coerceAtLeast(0f),
                height = (size.height - strokeWidth).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth, pathEffect = pathEffect)
        )
    }
}
