package com.limelight.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R

/**
 * Compose implementation of the About dialog.
 *
 * Replaces the previous Views XML setup (4 layouts + 9 selector drawables) with a
 * single declarative file. Window chrome (gradient + overlay corner + outline) is
 * provided by [dialogSurface], matching app_dialog_bg_cute.
 */

internal data class EcosystemProject(
    val badge: String,
    val title: String,
    val platform: String,
    val description: String,
    val url: String
)

internal val aboutDialogShape = RoundedCornerShape(20.dp)
private val actionShape = RoundedCornerShape(12.dp)
private val cardShape = RoundedCornerShape(16.dp)

internal object AboutDialogTags {
    const val HANDBOOK = "about_handbook"
    const val ECOSYSTEM = "about_ecosystem"
    const val STAR = "about_star"
    const val BILIBILI = "about_bilibili"
    const val GITHUB = "about_github"
    const val QQ = "about_qq"
    const val SITE = "about_site"
    const val CLOSE = "about_close"
    const val ECOSYSTEM_CLOSE = "about_ecosystem_close"
    const val ECOSYSTEM_OPEN = "about_ecosystem_open"

    fun ecosystemItem(index: Int) = "about_ecosystem_item_$index"
}

private fun Modifier.handleGamepadConfirm(onClick: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val nativeEvent = event.nativeKeyEvent
        if (nativeEvent.keyCode != KeyEvent.KEYCODE_BUTTON_A) {
            false
        } else {
            if (nativeEvent.action == KeyEvent.ACTION_UP) onClick()
            true
        }
    }

private fun Modifier.focusTarget(
    requester: FocusRequester,
    tag: String,
    onFocused: () -> Unit,
    up: FocusRequester = requester,
    down: FocusRequester = requester,
    left: FocusRequester = requester,
    right: FocusRequester = requester
): Modifier = focusRequester(requester)
    .focusProperties {
        this.up = up
        this.down = down
        this.left = left
        this.right = right
    }
    .onFocusChanged { if (it.isFocused) onFocused() }
    .testTag(tag)

@Composable
private fun dialogBrush(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            colorResource(R.color.app_dialog_surface_gradient_start),
            colorResource(R.color.app_dialog_surface_gradient_center),
            colorResource(R.color.app_dialog_surface_gradient_end)
        )
    )
}

@Composable
internal fun AboutDialogSurface(content: @Composable () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(240).dp
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = aboutDialogShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colorResource(R.color.app_dialog_outline))
    ) {
        Box(modifier = Modifier.background(dialogBrush())) {
            content()
        }
    }
}

@Composable
private fun AccentTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusModifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .handleGamepadConfirm(onClick)
            .focusable(),
        shape = actionShape,
        border = if (focused) {
            BorderStroke(2.dp, colorResource(R.color.app_dialog_accent_color))
        } else {
            null
        },
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (focused) {
                colorResource(R.color.app_dialog_accent_soft)
            } else {
                Color.Transparent
            },
            contentColor = colorResource(R.color.app_dialog_accent_color)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BilibiliCard(
    onClick: () -> Unit,
    focusModifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .handleGamepadConfirm(onClick)
            .focusable(),
        shape = cardShape,
        color = if (focused) {
            colorResource(R.color.app_dialog_surface_focused)
        } else {
            colorResource(R.color.about_dialog_link_surface)
        },
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) {
                colorResource(R.color.app_dialog_accent_color)
            } else {
                colorResource(R.color.about_dialog_panel_outline)
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 36.dp, height = 30.dp)
                    .background(Color.Transparent, RoundedCornerShape(8.dp))
            ) {
                Text(
                    stringResource(R.string.about_dialog_bilibili_badge),
                    color = colorResource(R.color.app_dialog_accent_color),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.about_dialog_bilibili_title),
                    color = colorResource(R.color.app_dialog_accent_color),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.about_dialog_bilibili_summary),
                    color = colorResource(R.color.app_dialog_subtitle_color),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_about_chevron_right),
                contentDescription = null,
                tint = colorResource(R.color.app_dialog_accent_color),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun AboutDialogContent(
    appName: String,
    versionInfo: String,
    onHandbook: () -> Unit,
    onEcosystem: () -> Unit,
    onStar: () -> Unit,
    onBilibili: () -> Unit,
    onGithub: () -> Unit,
    onQq: () -> Unit,
    onSite: () -> Unit,
    onClose: () -> Unit,
    initialFocusIndex: Int = 0,
    focusRequestGeneration: Int = 0,
    onFocusChanged: (Int) -> Unit = {}
) {
    val focusRequesters = remember { List(8) { FocusRequester() } }
    val targetIndex = initialFocusIndex.takeIf { it in focusRequesters.indices } ?: 7

    LaunchedEffect(targetIndex, focusRequestGeneration) {
        withFrameNanos { }
        focusRequesters[targetIndex].requestFocus()
    }

    fun focusModifier(index: Int, up: Int, down: Int, left: Int, right: Int): Modifier {
        return Modifier.focusTarget(
            requester = focusRequesters[index],
            tag = when (index) {
                0 -> AboutDialogTags.HANDBOOK
                1 -> AboutDialogTags.ECOSYSTEM
                2 -> AboutDialogTags.STAR
                3 -> AboutDialogTags.BILIBILI
                4 -> AboutDialogTags.GITHUB
                5 -> AboutDialogTags.QQ
                6 -> AboutDialogTags.SITE
                else -> AboutDialogTags.CLOSE
            },
            onFocused = { onFocusChanged(index) },
            up = focusRequesters[up],
            down = focusRequesters[down],
            left = focusRequesters[left],
            right = focusRequesters[right]
        )
    }

    AboutDialogSurface {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 28.dp, top = 28.dp, end = 32.dp, bottom = 72.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.vplus),
                    contentDescription = stringResource(R.string.app_label),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    appName,
                    color = colorResource(R.color.app_dialog_title_color),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    versionInfo,
                    color = colorResource(R.color.app_dialog_subtitle_color),
                    style = MaterialTheme.typography.bodySmall
                )
                Box(
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                        .width(60.dp)
                        .height(2.dp)
                        .background(colorResource(R.color.app_dialog_accent_color))
                )
                Text(
                    stringResource(R.string.about_dialog_description),
                    color = colorResource(R.color.app_dialog_title_color),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.about_dialog_project_info),
                    color = colorResource(R.color.app_dialog_subtitle_color),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    stringResource(R.string.about_dialog_thanks),
                    color = colorResource(R.color.app_dialog_subtitle_color),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                ) {
                    AccentTextButton(
                        stringResource(R.string.about_dialog_handbook_action),
                        onHandbook,
                        modifier = Modifier.weight(1f),
                        focusModifier = focusModifier(0, 7, 3, 0, 1)
                    )
                    AccentTextButton(
                        stringResource(R.string.about_dialog_ecosystem_action),
                        onEcosystem,
                        modifier = Modifier.weight(1f),
                        focusModifier = focusModifier(1, 7, 3, 0, 2)
                    )
                    AccentTextButton(
                        stringResource(R.string.about_dialog_star),
                        onStar,
                        modifier = Modifier.weight(1f),
                        focusModifier = focusModifier(2, 7, 3, 1, 2)
                    )
                }

                BilibiliCard(
                    onClick = onBilibili,
                    focusModifier = focusModifier(3, 0, 4, 3, 3)
                )
            }

            // bottom external links bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 5.dp)
            ) {
                AccentTextButton(
                    stringResource(R.string.about_dialog_github),
                    onGithub,
                    modifier = Modifier.weight(1f),
                    focusModifier = focusModifier(4, 3, 4, 4, 5)
                )
                AccentTextButton(
                    stringResource(R.string.about_dialog_qq),
                    onQq,
                    modifier = Modifier.weight(1f),
                    focusModifier = focusModifier(5, 3, 5, 4, 6)
                )
                AccentTextButton(
                    stringResource(R.string.about_dialog_official_site),
                    onSite,
                    modifier = Modifier.weight(1f),
                    focusModifier = focusModifier(6, 3, 6, 5, 6)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .then(focusModifier(7, 7, 1, 7, 7))
                    .handleGamepadConfirm(onClose)
                    .focusable()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_about_close),
                    contentDescription = stringResource(R.string.about_dialog_close),
                    tint = colorResource(R.color.app_dialog_title_color)
                )
            }
        }
    }
}

@Composable
internal fun EcosystemDialogContent(
    projects: List<EcosystemProject>,
    onOpen: (EcosystemProject) -> Unit,
    onClose: () -> Unit,
    initialFocusIndex: Int = 0,
    onFocusChanged: (Int) -> Unit = {}
) {
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemFocus = remember(projects.size) { List(projects.size) { FocusRequester() } }
    val closeFocus = remember { FocusRequester() }
    val openFocus = remember { FocusRequester() }
    val validInitialIndex = initialFocusIndex.takeIf { it in projects.indices }

    LaunchedEffect(validInitialIndex, projects) {
        withFrameNanos { }
        validInitialIndex?.let(itemFocus::get)?.requestFocus() ?: closeFocus.requestFocus()
    }

    AboutDialogSurface {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    stringResource(R.string.about_dialog_ecosystem_title),
                    color = colorResource(R.color.app_dialog_title_color),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 48.dp)
                )
                Text(
                    stringResource(R.string.about_dialog_ecosystem_lead),
                    color = colorResource(R.color.app_dialog_subtitle_color),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp, end = 48.dp)
                )

                if (isLandscape) {
                    LandscapeEcosystem(
                        projects,
                        onOpen,
                        itemFocus,
                        closeFocus,
                        openFocus,
                        validInitialIndex,
                        onFocusChanged
                    )
                } else {
                    PortraitEcosystem(
                        projects,
                        onOpen,
                        itemFocus,
                        closeFocus,
                        onFocusChanged
                    )
                }

                Text(
                    stringResource(R.string.about_dialog_ecosystem_footer),
                    color = colorResource(R.color.app_dialog_subtitle_color),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .focusTarget(
                        requester = closeFocus,
                        tag = AboutDialogTags.ECOSYSTEM_CLOSE,
                        onFocused = { onFocusChanged(-1) },
                        down = itemFocus.firstOrNull() ?: closeFocus
                    )
                    .handleGamepadConfirm(onClose)
                    .focusable()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_about_close),
                    contentDescription = stringResource(R.string.about_dialog_close),
                    tint = colorResource(R.color.app_dialog_title_color)
                )
            }
        }
    }
}

@Composable
private fun PortraitEcosystem(
    projects: List<EcosystemProject>,
    onOpen: (EcosystemProject) -> Unit,
    itemFocus: List<FocusRequester>,
    closeFocus: FocusRequester,
    onFocusChanged: (Int) -> Unit
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val columns = if (widthDp >= 600) 2 else 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        projects.indices.chunked(columns).forEach { rowIndices ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowIndices.forEach { index ->
                    val project = projects[index]
                    val upIndex = GridFocusNavigator.nextIndex(
                        index,
                        projects.size,
                        columns,
                        GridFocusDirection.UP
                    )
                    val downIndex = GridFocusNavigator.nextIndex(
                        index,
                        projects.size,
                        columns,
                        GridFocusDirection.DOWN
                    )
                    val leftIndex = GridFocusNavigator.nextIndex(
                        index,
                        projects.size,
                        columns,
                        GridFocusDirection.LEFT
                    )
                    val rightIndex = GridFocusNavigator.nextIndex(
                        index,
                        projects.size,
                        columns,
                        GridFocusDirection.RIGHT
                    )
                    EcosystemCard(
                        project = project,
                        onOpen = onOpen,
                        focusModifier = Modifier
                            .weight(1f)
                            .focusTarget(
                                requester = itemFocus[index],
                                tag = AboutDialogTags.ecosystemItem(index),
                                onFocused = { onFocusChanged(index) },
                                up = if (upIndex == GridFocusNavigator.CLOSE_TARGET) {
                                    closeFocus
                                } else {
                                    itemFocus[upIndex]
                                },
                                down = itemFocus[downIndex],
                                left = itemFocus[leftIndex],
                                right = itemFocus[rightIndex]
                            )
                    )
                }
                repeat(columns - rowIndices.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LandscapeEcosystem(
    projects: List<EcosystemProject>,
    onOpen: (EcosystemProject) -> Unit,
    itemFocus: List<FocusRequester>,
    closeFocus: FocusRequester,
    openFocus: FocusRequester,
    initialFocusIndex: Int?,
    onFocusChanged: (Int) -> Unit
) {
    var selected by remember(initialFocusIndex) { mutableIntStateOf(initialFocusIndex ?: 0) }
    val project = projects[selected]

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(180.dp)
        ) {
            projects.forEachIndexed { index, p ->
                var focused by remember { mutableStateOf(false) }
                val selectedBg = if (index == selected) {
                    colorResource(R.color.app_dialog_accent_soft)
                } else {
                    Color.Transparent
                }
                Surface(
                    onClick = {
                        selected = index
                        onFocusChanged(index)
                        itemFocus[index].requestFocus()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = selectedBg,
                    border = if (focused) {
                        BorderStroke(2.dp, colorResource(R.color.app_dialog_accent_color))
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusTarget(
                            requester = itemFocus[index],
                            tag = AboutDialogTags.ecosystemItem(index),
                            onFocused = {
                                selected = index
                                onFocusChanged(index)
                            },
                            up = if (index == 0) closeFocus else itemFocus[index - 1],
                            down = itemFocus.getOrElse(index + 1) { itemFocus[index] },
                            right = openFocus
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .handleGamepadConfirm { onOpen(p) }
                        .focusable()
                ) {
                    Text(
                        p.title,
                        color = colorResource(R.color.app_dialog_title_color),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(
                            start = 12.dp,
                            top = 7.dp,
                            end = 10.dp,
                            bottom = 7.dp
                        )
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .padding(start = 14.dp, end = 18.dp)
                .width(1.dp)
                .height(300.dp)
                .background(colorResource(R.color.about_dialog_panel_outline))
        )

        Column(modifier = Modifier.weight(1f)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 44.dp, height = 38.dp)
                    .background(
                        colorResource(R.color.app_dialog_accent_soft),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    project.badge,
                    color = colorResource(R.color.app_dialog_accent_color),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                project.title,
                color = colorResource(R.color.app_dialog_title_color),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                project.platform,
                color = colorResource(R.color.app_dialog_accent_color),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                project.description,
                color = colorResource(R.color.app_dialog_subtitle_color),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 14.dp)
            )
            AccentTextButton(
                stringResource(R.string.about_dialog_ecosystem_open_action),
                { onOpen(project) },
                modifier = Modifier.padding(top = 18.dp),
                focusModifier = Modifier.focusTarget(
                    requester = openFocus,
                    tag = AboutDialogTags.ECOSYSTEM_OPEN,
                    onFocused = { onFocusChanged(selected) },
                    left = itemFocus[selected]
                )
            )
        }
    }
}

@Composable
private fun EcosystemCard(
    project: EcosystemProject,
    onOpen: (EcosystemProject) -> Unit,
    focusModifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onOpen(project) },
        shape = cardShape,
        color = if (focused) {
            colorResource(R.color.app_dialog_surface_focused)
        } else {
            colorResource(R.color.about_dialog_link_surface)
        },
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) {
                colorResource(R.color.app_dialog_accent_color)
            } else {
                colorResource(R.color.about_dialog_panel_outline)
            }
        ),
        modifier = Modifier
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .handleGamepadConfirm { onOpen(project) }
            .focusable()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            colorResource(R.color.app_dialog_accent_soft),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        project.badge,
                        color = colorResource(R.color.app_dialog_accent_color),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        project.title,
                        color = colorResource(R.color.app_dialog_title_color),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                    Text(
                        project.platform,
                        color = colorResource(R.color.app_dialog_accent_color),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                project.description,
                color = colorResource(R.color.app_dialog_subtitle_color),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
