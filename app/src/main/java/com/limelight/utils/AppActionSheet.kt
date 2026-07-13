package com.limelight.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.R

object AppActionSheet {
    data class Action(
        val id: Int,
        val title: CharSequence,
        val destructive: Boolean = false,
        val checked: Boolean? = null,
        val sectionStart: Boolean = false,
        val opensSubmenu: Boolean = false
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

        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onDismiss?.invoke(selectedAction) }
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.attributes = window.attributes.apply { gravity = Gravity.BOTTOM }
        }
        return dialog
    }

    @Composable
    private fun AppActionSheetTheme(content: @Composable () -> Unit) {
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
        val shape = RoundedCornerShape(22.dp)
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
                    .padding(bottom = 8.dp)
            ) {
                ActionSheetHeader(title, subtitle, activeStatus)
                val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(actions, key = { it.id }) { action ->
                        ActionSheetRow(action, onAction)
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionSheetHeader(title: String, subtitle: String?, activeStatus: Boolean) {
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
    private fun ActionSheetRow(action: Action, onAction: (Action) -> Unit) {
        val rowShape = RoundedCornerShape(12.dp)
        Column {
            if (action.sectionStart) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    thickness = 1.dp,
                    color = colorResource(R.color.app_action_sheet_divider)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(rowShape)
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
                if (action.checked == true) {
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
}
