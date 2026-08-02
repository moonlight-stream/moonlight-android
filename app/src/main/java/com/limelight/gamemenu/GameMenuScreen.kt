package com.limelight.gamemenu

// Game Menu theme, adaptive shell, header, and footer.

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.limelight.R

private val GameMenuDialogShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
internal val GameMenuCardShape = RoundedCornerShape(10.dp)
internal val GameMenuControlRadius = 10.dp
internal val GameMenuControlShape = RoundedCornerShape(GameMenuControlRadius)
private const val GAME_MENU_MAX_HEIGHT_FRACTION = 0.90f
private const val GAME_MENU_WIDE_LAYOUT_MIN_WIDTH_DP = 576
private const val ORIENTATION_MISMATCH_THRESHOLD = 1.5f

internal object GameMenuDimens {
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

internal object GameMenuSliderSpec {
    val height = 36.dp
    val thumbSize = DpSize(width = 3.dp, height = 34.dp)
    val trackHeight = 12.dp
    val thumbTrackGap = 4.dp
    val trackInsideCorner = 1.5.dp
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

@Composable
internal fun GameMenuScreen(
    state: GameMenuComposeUiState,
    callbacks: GameMenuCallbacks,
    useFabricTexture: Boolean = true
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
                            darkTheme = palette.darkTheme,
                            textureEnabled = useFabricTexture
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
        GameMenuHeader(state, callbacks)

        if (!state.isSubmenu) {
            QuickActionRow(
                actions = state.quickActions,
                superOptions = state.superOptions,
                editMode = state.quickEditMode,
                onAction = callbacks.onQuickAction,
                onSuperOptionClick = callbacks.onOptionClick,
                onEmptySuperCommandClick = callbacks.onEmptySuperCommandClick,
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
    darkTheme: Boolean,
    textureEnabled: Boolean
): Modifier = if (textureEnabled) {
    gameMenuFabricTexture(baseColor, darkTheme)
} else {
    background(baseColor)
}

private fun Modifier.gameMenuFabricTexture(
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
    callbacks: GameMenuCallbacks
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
                    .clickable(onClick = callbacks.onBack)
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
            state.deviceQuickOptions.forEach { option ->
                HeaderDeviceQuickAction(
                    option = option,
                    iconRes = callbacks.iconForOption(option.iconKey),
                    onToggle = callbacks.onInlineToggle
                )
                Spacer(Modifier.width(GameMenuDimens.tight))
            }
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
                    .clickable(onClick = callbacks.onCrownToggle)
                    .padding(7.dp)
            )
        }
    }
}

@Composable
private fun HeaderDeviceQuickAction(
    option: GameMenu.MenuOption,
    @DrawableRes iconRes: Int,
    onToggle: (GameMenu.InlineControl.Toggle) -> Unit
) {
    val toggle = option.inlineControl as? GameMenu.InlineControl.Toggle ?: return
    val view = LocalView.current
    val shape = CircleShape
    val accent = colorResource(R.color.game_menu_accent)
    val stateDescription = stringResource(
        if (toggle.checked) R.string.game_menu_on else R.string.game_menu_off
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = "${option.label}, $stateDescription" }
            .clip(shape)
            .background(accent.copy(alpha = if (toggle.checked) 0.18f else 0.06f))
            .border(
                GameMenuDimens.surfaceStroke,
                accent.copy(alpha = if (toggle.checked) 0.52f else 0.18f),
                shape
            )
            .gamepadFocusOutline(shape)
            .toggleable(
                value = toggle.checked,
                role = Role.Switch,
                onValueChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggle(toggle)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != 0) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = firstActionCharacter(option.label),
                color = colorResource(R.color.game_menu_text_primary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
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
