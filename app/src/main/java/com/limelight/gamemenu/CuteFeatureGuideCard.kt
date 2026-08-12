package com.limelight.gamemenu

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.R
import com.limelight.ui.UiDismissKeyHandler
import com.limelight.ui.theme.AppShapes

@Composable
internal fun CuteFeatureGuideCard(
    eyebrow: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onSkip: () -> Unit
) {
    val accent = colorResource(R.color.game_menu_accent)
    val ink = Color(0xFF4C4346)
    val mutedInk = Color(0xFF6C6063)
    val paper = Color(0xFFFFF8E8)
    val skipFocusRequester = remember { FocusRequester() }
    val actionFocusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    val isTelevision = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val shouldRequestInitialFocus = isTelevision || inputModeManager.inputMode == InputMode.Keyboard
    var isActionLaidOut by remember { mutableStateOf(false) }

    BackHandler(onBack = onSkip)
    LaunchedEffect(isActionLaidOut, shouldRequestInitialFocus) {
        if (isActionLaidOut && shouldRequestInitialFocus) {
            if (isTelevision) inputModeManager.requestInputMode(InputMode.Keyboard)
            actionFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .widthIn(min = 252.dp, max = 316.dp)
            .onPreviewKeyEvent { event ->
                UiDismissKeyHandler.handle(
                    event.nativeKeyEvent.action,
                    event.nativeKeyEvent.keyCode,
                    onSkip
                )
            }
            .drawBehind {
                val leaderSpace = 34.dp.toPx()
                val wobble = 2.dp.toPx()
                val paperPath = Path().apply {
                    moveTo(7.dp.toPx(), leaderSpace + wobble)
                    lineTo(size.width * 0.24f, leaderSpace)
                    lineTo(size.width * 0.49f, leaderSpace + wobble)
                    lineTo(size.width * 0.75f, leaderSpace - 1.dp.toPx())
                    lineTo(size.width - 7.dp.toPx(), leaderSpace + wobble)
                    quadraticTo(size.width, leaderSpace + 8.dp.toPx(), size.width - 1.dp.toPx(), leaderSpace + 16.dp.toPx())
                    lineTo(size.width, size.height - 9.dp.toPx())
                    quadraticTo(size.width - 2.dp.toPx(), size.height, size.width - 12.dp.toPx(), size.height)
                    lineTo(size.width * 0.72f, size.height - 1.dp.toPx())
                    lineTo(size.width * 0.46f, size.height)
                    lineTo(size.width * 0.20f, size.height - 2.dp.toPx())
                    lineTo(7.dp.toPx(), size.height)
                    quadraticTo(0f, size.height - 6.dp.toPx(), 1.dp.toPx(), size.height - 15.dp.toPx())
                    lineTo(0f, leaderSpace + 11.dp.toPx())
                    quadraticTo(1.dp.toPx(), leaderSpace + 4.dp.toPx(), 7.dp.toPx(), leaderSpace + wobble)
                    close()
                }
                drawPath(paperPath, paper)
                drawPath(paperPath, Color(0xFFD8CABC), style = Stroke(1.1.dp.toPx()))
            }
    ) {
        PaperNoteConnector(Modifier.fillMaxSize(), accent)
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = eyebrow,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
            Text(
                text = title,
                color = ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                lineHeight = 29.sp
            )
            HandDrawnUnderline(accent)
            Text(
                text = body,
                color = mutedInk,
                fontSize = 16.sp,
                letterSpacing = 0.3.sp,
                lineHeight = 25.sp
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSkip,
                    shape = AppShapes.medium,
                    modifier = Modifier
                        .focusRequester(skipFocusRequester)
                        .focusProperties {
                            left = skipFocusRequester
                            right = actionFocusRequester
                            up = skipFocusRequester
                            down = skipFocusRequester
                        }
                ) {
                    Text(
                        text = stringResource(R.string.feature_guide_skip),
                        color = mutedInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = "│",
                    modifier = Modifier.clearAndSetSemantics { },
                    color = Color(0xFFD8CABC),
                    fontSize = 15.sp
                )
                TextButton(
                    onClick = onAction,
                    shape = AppShapes.medium,
                    modifier = Modifier
                        .focusRequester(actionFocusRequester)
                        .onGloballyPositioned { isActionLaidOut = true }
                        .focusProperties {
                            left = skipFocusRequester
                            right = actionFocusRequester
                            up = actionFocusRequester
                            down = actionFocusRequester
                        }
                ) {
                    Text(
                        text = actionLabel,
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperNoteConnector(modifier: Modifier, accent: Color) {
    Canvas(modifier = modifier) {
        val leaderBottom = 29.dp.toPx()
        val leader = Path().apply {
            moveTo(size.width * 0.72f, 8.dp.toPx())
            cubicTo(size.width * 0.72f, 15.dp.toPx(), 132.dp.toPx(), 9.dp.toPx(), 108.dp.toPx(), 16.dp.toPx())
        }
        drawPath(
            leader,
            accent,
            style = Stroke(
                2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
            )
        )
        val arrowTip = Offset(86.dp.toPx(), leaderBottom)
        val back = Offset(0.860f, -0.511f)
        val side = Offset(0.511f, 0.860f)
        val wingLength = 13.dp.toPx()
        val wingSpread = 5.5.dp.toPx()
        val arrow = Path().apply {
            moveTo(
                arrowTip.x + back.x * wingLength + side.x * wingSpread,
                arrowTip.y + back.y * wingLength + side.y * wingSpread
            )
            lineTo(arrowTip.x, arrowTip.y)
            lineTo(
                arrowTip.x + back.x * wingLength - side.x * wingSpread,
                arrowTip.y + back.y * wingLength - side.y * wingSpread
            )
        }
        drawPath(
            arrow,
            accent,
            style = Stroke(
                2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        rotate(-9f, Offset(28.dp.toPx(), 37.dp.toPx())) {
            drawRoundRect(
                color = Color(0xFFFF978F),
                topLeft = Offset(4.dp.toPx(), 28.dp.toPx()),
                size = Size(52.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            var x = 9.dp.toPx()
            while (x < 52.dp.toPx()) {
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(x, 31.dp.toPx()),
                    Offset(x + 5.dp.toPx(), 43.dp.toPx()),
                    1.dp.toPx()
                )
                x += 8.dp.toPx()
            }
        }
    }
}

@Composable
private fun HandDrawnUnderline(color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
    ) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.45f)
            cubicTo(
                size.width * 0.22f, size.height * 0.05f,
                size.width * 0.44f, size.height * 0.88f,
                size.width * 0.66f, size.height * 0.42f
            )
            cubicTo(
                size.width * 0.78f, size.height * 0.18f,
                size.width * 0.90f, size.height * 0.72f,
                size.width, size.height * 0.34f
            )
        }
        drawPath(path, color.copy(alpha = 0.82f), style = Stroke(width = 1.8.dp.toPx()))
        drawCircle(color.copy(alpha = 0.52f), radius = 1.3.dp.toPx(), center = Offset(size.width * 0.92f, size.height * 0.78f))
    }
}
