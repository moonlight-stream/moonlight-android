package com.limelight.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.R
import com.limelight.ui.UiDismissKeyHandler
import com.limelight.ui.theme.AppShapes

object AppActionSheet {
    data class Action(
        val id: Int,
        val title: CharSequence,
        val destructive: Boolean = false,
        val checked: Boolean? = null,
        val sectionStart: Boolean = false,
        val opensSubmenu: Boolean = false,
        val trailingText: CharSequence? = null
    )

    fun show(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence? = null,
        activeStatus: Boolean = false,
        actions: List<Action>,
        onAction: (Action) -> Unit,
        onDismiss: ((Action?) -> Unit)? = null
    ): Dialog {
        val dialog = ComponentDialog(context, R.style.AppActionSheetStyle)
        var selectedAction: Action? = null
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AppActionSheetTheme {
                    ActionSheetContent(
                        title = title.toString(),
                        subtitle = subtitle?.toString(),
                        activeStatus = activeStatus,
                        actions = actions,
                        onAction = { action ->
                            selectedAction = action
                            dialog.dismiss()
                            onAction(action)
                        }
                    )
                }
            }
        }

        dialog.setOnDismissListener { onDismiss?.invoke(selectedAction) }
        prepareDialog(dialog, composeView)
        return dialog
    }

    fun showMultiSelect(
        context: Context,
        title: CharSequence,
        actions: List<Action>,
        confirmLabel: CharSequence,
        cancelLabel: CharSequence,
        resetLabel: CharSequence? = null,
        minimumSelectionCount: Int = 0,
        onConfirm: (Set<Int>) -> Unit,
        onReset: (() -> Unit)? = null
    ): Dialog {
        val dialog = ComponentDialog(context, R.style.AppActionSheetStyle)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AppActionSheetTheme {
                    var selectedIds by remember(actions) {
                        mutableStateOf<Set<Int>>(
                            actions.filter { it.checked == true }
                                .mapTo(linkedSetOf()) { it.id }
                        )
                    }
                    MultiSelectActionSheetContent(
                        title = title.toString(),
                        actions = actions,
                        selectedIds = selectedIds,
                        minimumSelectionCount = minimumSelectionCount,
                        confirmLabel = confirmLabel.toString(),
                        cancelLabel = cancelLabel.toString(),
                        resetLabel = resetLabel?.toString(),
                        onToggle = { action ->
                            selectedIds = if (action.id in selectedIds) {
                                if (selectedIds.size > minimumSelectionCount) selectedIds - action.id
                                else selectedIds
                            } else {
                                selectedIds + action.id
                            }
                        },
                        onConfirm = {
                            dialog.dismiss()
                            onConfirm(selectedIds)
                        },
                        onCancel = dialog::dismiss,
                        onReset = onReset?.let { reset ->
                            {
                                dialog.dismiss()
                                reset()
                            }
                        }
                    )
                }
            }
        }
        prepareDialog(dialog, composeView)
        return dialog
    }

    @Suppress("DEPRECATION")
    internal fun prepareDialog(dialog: ComponentDialog, contentView: ComposeView) {
        dialog.setContentView(contentView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnKeyListener { _, keyCode, event ->
            UiDismissKeyHandler.handle(event.action, keyCode, dialog::cancel)
        }

        dialog.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            (contentView.context as? Activity)?.window?.let { hostWindow ->
                window.decorView.systemUiVisibility = hostWindow.decorView.systemUiVisibility
                if (hostWindow.attributes.flags and
                    WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
                ) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
            window.attributes = window.attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
            }
        }

        dialog.show()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @Composable
    internal fun AppActionSheetTheme(content: @Composable () -> Unit) {
        val accent = colorResource(R.color.ui_shell_accent)
        val surface = colorResource(R.color.app_dialog_surface)
        val primary = colorResource(R.color.app_dialog_text_primary)
        val secondary = colorResource(R.color.app_dialog_text_secondary)
        val scheme = if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = accent,
                surface = surface,
                onSurface = primary,
                onSurfaceVariant = secondary
            )
        } else {
            lightColorScheme(
                primary = accent,
                surface = surface,
                onSurface = primary,
                onSurfaceVariant = secondary
            )
        }
        MaterialTheme(colorScheme = scheme, content = content)
    }

    @Composable
    private fun ActionSheetContent(
        title: String,
        subtitle: String?,
        activeStatus: Boolean,
        actions: List<Action>,
        onAction: (Action) -> Unit
    ) {
        val initialFocusRequester = remember { FocusRequester() }
        val shouldRequestFocus = isControllerFocusMode()
        val initialFocusPlaced = remember { mutableStateOf(false) }
        LaunchedEffect(actions, shouldRequestFocus, initialFocusPlaced.value) {
            if (shouldRequestFocus && actions.isNotEmpty() && initialFocusPlaced.value) {
                initialFocusRequester.requestFocus()
            }
        }
        ActionSheetContainer {
            ActionSheetHeader(title, subtitle, activeStatus)
            val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(actions, key = { _, action -> action.id }) { index, action ->
                    ActionSheetRow(
                        action,
                        onAction,
                        if (index == 0) {
                            Modifier
                                .focusRequester(initialFocusRequester)
                                .onGloballyPositioned { initialFocusPlaced.value = true }
                        } else Modifier
                    )
                }
            }
        }
    }

    @Composable
    private fun MultiSelectActionSheetContent(
        title: String,
        actions: List<Action>,
        selectedIds: Set<Int>,
        minimumSelectionCount: Int,
        confirmLabel: String,
        cancelLabel: String,
        resetLabel: String?,
        onToggle: (Action) -> Unit,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        val initialFocusRequester = remember { FocusRequester() }
        val shouldRequestFocus = isControllerFocusMode()
        val initialFocusPlaced = remember { mutableStateOf(false) }
        LaunchedEffect(actions, shouldRequestFocus, initialFocusPlaced.value) {
            if (shouldRequestFocus && initialFocusPlaced.value) {
                initialFocusRequester.requestFocus()
            }
        }
        ActionSheetContainer {
            ActionSheetHeader(title, null, false)
            val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.54f).dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(actions, key = { _, action -> action.id }) { index, action ->
                    ActionSheetRow(
                        action = action.copy(checked = action.id in selectedIds),
                        onAction = onToggle,
                        modifier = if (index == 0) {
                            Modifier
                                .focusRequester(initialFocusRequester)
                                .onGloballyPositioned { initialFocusPlaced.value = true }
                        } else Modifier
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                thickness = 1.dp,
                color = colorResource(R.color.app_action_sheet_divider)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (resetLabel != null && onReset != null) {
                    ActionSheetFooterAction(resetLabel, onReset)
                }
                Spacer(Modifier.weight(1f))
                ActionSheetFooterAction(
                    cancelLabel,
                    onCancel,
                    modifier = if (actions.isEmpty()) {
                        Modifier
                            .focusRequester(initialFocusRequester)
                            .onGloballyPositioned { initialFocusPlaced.value = true }
                    } else Modifier
                )
                ActionSheetFooterAction(
                    label = confirmLabel,
                    onClick = onConfirm,
                    enabled = selectedIds.size >= minimumSelectionCount
                )
            }
        }
    }

    @Composable
    internal fun ActionSheetContainer(content: @Composable ColumnScope.() -> Unit) {
        val shape = AppShapes.overlay
        val outline = colorResource(R.color.app_dialog_outline)
        val gradient = Brush.verticalGradient(
            listOf(
                colorResource(R.color.app_dialog_surface_gradient_start),
                colorResource(R.color.app_dialog_surface_gradient_center),
                colorResource(R.color.app_dialog_surface_gradient_end)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(gradient)
                    .border(1.dp, outline, shape)
                    .padding(bottom = 8.dp),
                content = content
            )
        }
    }

    @Composable
    internal fun ActionSheetFooterAction(
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        primary: Boolean = false,
        compact: Boolean = false
    ) {
        var focused by remember { mutableStateOf(false) }
        val shape = AppShapes.medium
        Box(
            modifier = modifier
                .onFocusChanged { focused = it.isFocused }
                .heightIn(min = if (compact) 32.dp else 42.dp)
                .clip(shape)
                .then(
                    when {
                        primary -> Modifier.background(MaterialTheme.colorScheme.primary)
                        focused -> Modifier.background(colorResource(R.color.app_dialog_surface_focused))
                        else -> Modifier
                    }
                )
                .then(
                    if (focused) {
                        Modifier.border(
                            2.dp,
                            if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            shape
                        )
                    } else {
                        Modifier
                    }
                )
                .onPreviewKeyEvent { event ->
                    val nativeEvent = event.nativeKeyEvent
                    if (enabled && nativeEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                        if (nativeEvent.action == KeyEvent.ACTION_UP) onClick()
                        true
                    } else {
                        false
                    }
                }
                .clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled)
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 6.dp else 10.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (primary) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.38f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
                },
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    internal fun ActionSheetHeader(title: String, subtitle: String?, activeStatus: Boolean) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 15.dp, end = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (activeStatus) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    private fun ActionSheetRow(
        action: Action,
        onAction: (Action) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val rowShape = AppShapes.medium
        var focused by remember(action.id) { mutableStateOf(false) }
        Column {
            if (action.sectionStart) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    thickness = 1.dp,
                    color = colorResource(R.color.app_action_sheet_divider)
                )
            }
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(rowShape)
                    .then(
                        if (focused) Modifier.background(colorResource(R.color.app_dialog_surface_focused))
                        else Modifier
                    )
                    .then(
                        if (focused) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, rowShape)
                        } else {
                            Modifier
                        }
                    )
                    .onPreviewKeyEvent { event ->
                        val nativeEvent = event.nativeKeyEvent
                        if (nativeEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                            if (nativeEvent.action == KeyEvent.ACTION_UP) onAction(action)
                            true
                        } else {
                            false
                        }
                    }
                    .clickable { onAction(action) }
                    .focusable()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.title.toString(),
                    modifier = Modifier.weight(1f),
                    color = if (action.destructive) colorResource(R.color.app_action_sheet_danger)
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!action.trailingText.isNullOrEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = action.trailingText.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (action.checked == true) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (action.opensSubmenu) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "›",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 20.sp
                    )
                }
            }
        }
    }

    @Composable
    internal fun isControllerFocusMode(): Boolean {
        val configuration = LocalConfiguration.current
        val isTelevision = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        return isTelevision || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    }
}
