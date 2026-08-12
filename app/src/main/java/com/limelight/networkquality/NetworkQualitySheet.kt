package com.limelight.networkquality

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.R
import com.limelight.ui.theme.AppShapes
import com.limelight.utils.AppActionSheet
import java.util.Locale

object NetworkQualitySheet {
    class TestingHandle internal constructor(
        private val activity: Activity,
        private val dialog: ComponentDialog,
        private val progressState: MutableState<Float>
    ) {
        fun updateProgress(receivedBytes: Long, totalBytes: Long) {
            activity.runOnUiThread {
                progressState.value = if (totalBytes <= 0) 0f
                else (receivedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            }
        }

        fun dismiss() {
            activity.runOnUiThread { dialog.dismiss() }
        }
    }

    fun showTesting(
        activity: Activity,
        computerName: String,
        onCancel: () -> Unit
    ): TestingHandle {
        val progress = mutableFloatStateOf(0f)
        val dialog = ComponentDialog(activity, R.style.AppActionSheetStyle)
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val cancelFocusRequester = remember { FocusRequester() }
                val cancelPlaced = remember { mutableStateOf(false) }
                LaunchedEffect(cancelPlaced.value) {
                    if (cancelPlaced.value) cancelFocusRequester.requestFocus()
                }
                AppActionSheet.AppActionSheetTheme {
                    AppActionSheet.ActionSheetContainer {
                        AppActionSheet.ActionSheetHeader(
                            activity.getString(R.string.network_quality_test_title),
                            computerName,
                            true
                        )
                        TestingContent(progress.value)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = if (compact) 2.dp else 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AppActionSheet.ActionSheetFooterAction(
                                activity.getString(R.string.dialog_button_cancel),
                                onClick = dialog::cancel,
                                compact = compact,
                                modifier = Modifier
                                    .focusRequester(cancelFocusRequester)
                                    .onGloballyPositioned { cancelPlaced.value = true }
                            )
                        }
                    }
                }
            }
        }
        AppActionSheet.prepareDialog(dialog, composeView)
        dialog.setOnCancelListener { onCancel() }
        return TestingHandle(activity, dialog, progress)
    }

    fun showResult(
        context: Context,
        computerName: String,
        result: StreamNetworkTestResult,
        recommendation: StreamNetworkRecommendation?,
        onSaveToSceneOne: () -> Unit,
        onContinue: () -> Unit
    ) {
        val dialog = ComponentDialog(context, R.style.AppActionSheetStyle)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val saveFocusRequester = remember { FocusRequester() }
                val closeFocusRequester = remember { FocusRequester() }
                val continueFocusRequester = remember { FocusRequester() }
                val initialFocusPlaced = remember { mutableStateOf(false) }
                LaunchedEffect(initialFocusPlaced.value) {
                    if (initialFocusPlaced.value) {
                        if (recommendation == null) closeFocusRequester.requestFocus()
                        else continueFocusRequester.requestFocus()
                    }
                }
                AppActionSheet.AppActionSheetTheme {
                    AppActionSheet.ActionSheetContainer {
                        AppActionSheet.ActionSheetHeader(
                            context.getString(R.string.network_quality_test_title),
                            computerName,
                            true
                        )
                        ResultContent(
                            context,
                            result,
                            recommendation,
                            onSaveToSceneOne,
                            compact,
                            Modifier
                                .focusRequester(saveFocusRequester)
                                .focusProperties {
                                    left = saveFocusRequester
                                    right = saveFocusRequester
                                    down = continueFocusRequester
                                }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = if (compact) 2.dp else 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AppActionSheet.ActionSheetFooterAction(
                                context.getString(R.string.dialog_button_close),
                                dialog::dismiss,
                                primary = recommendation == null,
                                compact = compact,
                                modifier = Modifier
                                    .focusRequester(closeFocusRequester)
                                    .then(
                                        if (recommendation == null) {
                                            Modifier.onGloballyPositioned { initialFocusPlaced.value = true }
                                        } else Modifier
                                    )
                                    .focusProperties {
                                        left = closeFocusRequester
                                        right = if (recommendation == null) closeFocusRequester else continueFocusRequester
                                        up = if (recommendation == null) closeFocusRequester else saveFocusRequester
                                    }
                            )
                            if (recommendation != null) {
                                Spacer(Modifier.width(8.dp))
                                AppActionSheet.ActionSheetFooterAction(
                                    context.getString(R.string.network_quality_use_and_continue),
                                    onClick = {
                                        dialog.dismiss()
                                        onContinue()
                                    },
                                    primary = true,
                                    compact = compact,
                                    modifier = Modifier
                                        .focusRequester(continueFocusRequester)
                                        .onGloballyPositioned { initialFocusPlaced.value = true }
                                        .focusProperties {
                                            left = closeFocusRequester
                                            right = continueFocusRequester
                                            up = saveFocusRequester
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        AppActionSheet.prepareDialog(dialog, composeView)
    }

    fun showError(context: Context, computerName: String, message: String) {
        AppActionSheet.show(
            context = context,
            title = context.getString(R.string.network_quality_test_failed),
            subtitle = computerName,
            actions = listOf(AppActionSheet.Action(0, message)),
            onAction = {}
        )
    }

    fun showStartWarning(
        context: Context,
        computerName: String,
        currentBitrateKbps: Int,
        recommendation: StreamNetworkRecommendation,
        onContinue: () -> Unit,
        onUseRecommendation: () -> Unit
    ) {
        val dialog = ComponentDialog(context, R.style.AppActionSheetStyle)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val continueFocusRequester = remember { FocusRequester() }
                val recommendedFocusRequester = remember { FocusRequester() }
                val recommendedPlaced = remember { mutableStateOf(false) }
                LaunchedEffect(recommendedPlaced.value) {
                    if (recommendedPlaced.value) recommendedFocusRequester.requestFocus()
                }
                AppActionSheet.AppActionSheetTheme {
                    AppActionSheet.ActionSheetContainer {
                        AppActionSheet.ActionSheetHeader(computerName, context.getString(R.string.pcview_menu_header_online), true)
                        WarningContent(context, currentBitrateKbps, recommendation)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AppActionSheet.ActionSheetFooterAction(
                                context.getString(R.string.network_quality_start_anyway),
                                onClick = {
                                    dialog.dismiss()
                                    onContinue()
                                },
                                modifier = Modifier
                                    .focusRequester(continueFocusRequester)
                                    .focusProperties {
                                        left = continueFocusRequester
                                        right = recommendedFocusRequester
                                    }
                            )
                            AppActionSheet.ActionSheetFooterAction(
                                context.getString(R.string.network_quality_start_recommended),
                                onClick = {
                                    dialog.dismiss()
                                    onUseRecommendation()
                                },
                                primary = true,
                                modifier = Modifier
                                    .focusRequester(recommendedFocusRequester)
                                    .onGloballyPositioned { recommendedPlaced.value = true }
                                    .focusProperties {
                                        left = continueFocusRequester
                                        right = recommendedFocusRequester
                                    }
                            )
                        }
                    }
                }
            }
        }
        AppActionSheet.prepareDialog(dialog, composeView)
    }

    @Composable
    private fun TestingContent(progress: Float) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                text = "${(progress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 29.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = colorResource(R.color.app_action_sheet_divider)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (progress <= 0f) {
                    "${stringResource(R.string.network_quality_measuring_response)}…"
                } else {
                    "${stringResource(R.string.network_quality_measuring_bandwidth)}…"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }

    @Composable
    private fun ResultContent(
        context: Context,
        result: StreamNetworkTestResult,
        recommendation: StreamNetworkRecommendation?,
        onSaveToSceneOne: () -> Unit,
        compact: Boolean,
        modifier: Modifier
    ) {
        val qualityLabel = when (result.quality) {
            StreamNetworkQuality.EXCELLENT -> R.string.network_quality_excellent
            StreamNetworkQuality.GOOD -> R.string.network_quality_good
            StreamNetworkQuality.FAIR -> R.string.network_quality_fair
            StreamNetworkQuality.POOR -> R.string.network_quality_poor
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 0.dp else 4.dp)
        ) {
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualitySummary(context.getString(qualityLabel), Modifier.weight(0.34f))
                    Spacer(Modifier.width(12.dp))
                    Metrics(context, result, Modifier.weight(0.66f), compact = true)
                }
                Spacer(Modifier.height(8.dp))
            } else {
                QualitySummary(context.getString(qualityLabel))
                Spacer(Modifier.height(12.dp))
                Metrics(context, result, Modifier.fillMaxWidth(), compact = false)
                Spacer(Modifier.height(12.dp))
            }
            if (recommendation != null) {
                RecommendationPanel(
                    context,
                    recommendation,
                    onSaveToSceneOne,
                    compact,
                    modifier
                )
            } else {
                NoStableRecommendationPanel(context, compact)
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Text(
                text = context.getString(
                    if (recommendation == null) R.string.network_quality_no_stable_recommendation_note
                    else R.string.network_quality_continue_note
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                lineHeight = 13.sp
            )
            Spacer(Modifier.height(if (compact) 0.dp else 4.dp))
        }
    }

    @Composable
    private fun NoStableRecommendationPanel(context: Context, compact: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .background(colorResource(R.color.app_dialog_accent_soft))
                .padding(if (compact) 10.dp else 14.dp)
        ) {
            Text(
                context.getString(R.string.network_quality_no_stable_recommendation),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    private fun QualitySummary(label: String, modifier: Modifier = Modifier) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.network_quality_headroom_summary),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    private fun Metrics(
        context: Context,
        result: StreamNetworkTestResult,
        modifier: Modifier,
        compact: Boolean
    ) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(
                Modifier.weight(1f),
                String.format(Locale.getDefault(), "%.1f", result.bandwidthMbps),
                context.getString(R.string.network_quality_stable_mbps),
                compact
            )
            Metric(
                Modifier.weight(1f),
                String.format(Locale.getDefault(), "%.0f", result.responseLatencyMs),
                context.getString(R.string.network_quality_response_ms),
                compact
            )
            Metric(
                Modifier.weight(1f),
                String.format(Locale.getDefault(), "±%.1f", result.responseJitterMs),
                context.getString(R.string.network_quality_jitter_ms),
                compact
            )
        }
    }

    @Composable
    private fun Metric(modifier: Modifier, value: String, label: String, compact: Boolean) {
        val shape = AppShapes.medium
        Column(
            modifier = modifier
                .clip(shape)
                .background(colorResource(R.color.app_dialog_surface_elevated))
                .border(1.dp, colorResource(R.color.app_action_sheet_divider), shape)
                .padding(horizontal = 4.dp, vertical = if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp, textAlign = TextAlign.Center)
        }
    }

    @Composable
    private fun RecommendationPanel(
        context: Context,
        recommendation: StreamNetworkRecommendation,
        onSaveToSceneOne: () -> Unit,
        compact: Boolean,
        modifier: Modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(AppShapes.large)
                .background(colorResource(R.color.app_dialog_accent_soft))
                .padding(if (compact) 10.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    context.getString(R.string.network_quality_stable_recommendation),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
                AppActionSheet.ActionSheetFooterAction(
                    context.getString(R.string.network_quality_save_scene_1),
                    onSaveToSceneOne,
                    compact = true,
                    modifier = modifier
                )
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${recommendation.width}×${recommendation.height} · ${recommendation.fps} FPS",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${recommendation.bitrateKbps / 1000} Mbps · ABR",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    private fun WarningContent(
        context: Context,
        currentBitrateKbps: Int,
        recommendation: StreamNetworkRecommendation
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(colorResource(R.color.app_dialog_accent_soft)),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                context.getString(R.string.network_quality_warning_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(7.dp))
            Text(
                context.getString(R.string.network_quality_warning_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                context.getString(
                    R.string.network_quality_warning_values,
                    currentBitrateKbps / 1000,
                    recommendation.bitrateKbps / 1000
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

}
