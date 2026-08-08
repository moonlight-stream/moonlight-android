package com.limelight.gamemenu

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.limelight.R
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GameMenuComposeFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cardIsSkippedAndChildControlsRemainReachable() {
        val cardFocused = AtomicBoolean(false)

        composeTestRule.setContent {
            val sliderValue = remember { mutableFloatStateOf(0.5f) }

            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .testTag("focusBeforeCard")
                        .size(48.dp)
                        .focusable()
                )
                GameMenuCard(
                    title = "Focus test",
                    onLongClick = {},
                    modifier = Modifier.onFocusChanged { cardFocused.set(it.isFocused) }
                ) {
                    Slider(
                        value = sliderValue.floatValue,
                        onValueChange = { sliderValue.floatValue = it },
                        modifier = Modifier
                            .focusProperties { canFocus = true }
                            .testTag("bitrateSlider")
                            .fillMaxWidth()
                    )
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        modifier = Modifier
                            .focusProperties { canFocus = true }
                            .testTag("gyroSwitch")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = true }
                            .clickable {}
                            .testTag("settingRow")
                    ) {
                        Text("Clickable setting")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("focusBeforeCard").requestFocus()
        composeTestRule.onNodeWithTag("focusBeforeCard").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithTag("bitrateSlider").assertIsFocused()
        assertFalse("The card container must not consume controller focus", cardFocused.get())

        composeTestRule.onNodeWithTag("bitrateSlider").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithTag("gyroSwitch").assertIsFocused()

        composeTestRule.onNodeWithTag("gyroSwitch").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithTag("settingRow").assertIsFocused()
    }

    @Test
    fun firstMenuOptionCanReceiveInitialFocusAndControllerConfirmation() {
        val activated = AtomicBoolean(false)
        lateinit var initialFocusRequester: FocusRequester
        val firstOption = GameMenu.MenuOption(
            "First option",
            false,
            Runnable { activated.set(true) },
            null,
            false
        )

        composeTestRule.setContent {
            initialFocusRequester = remember { FocusRequester() }
            MenuOptionColumn(
                options = listOf(firstOption),
                iconForOption = { 0 },
                onOptionClick = { it.runnable?.run() },
                onInlineToggle = {},
                onSegmentClick = {},
                initialFocusRequester = initialFocusRequester
            )
        }

        composeTestRule.runOnIdle { initialFocusRequester.requestFocus() }
        composeTestRule.onNodeWithText("First option").assertIsFocused()
        composeTestRule.onNodeWithText("First option").performKeyInput {
            pressKey(Key.DirectionCenter)
        }

        assertTrue(activated.get())
    }

    @Test
    fun featureGuideKeepsRemoteFocusOnItsActions() {
        val advanced = AtomicBoolean(false)

        composeTestRule.setContent {
            val tvConfiguration = Configuration(LocalConfiguration.current).apply {
                uiMode = uiMode and Configuration.UI_MODE_TYPE_MASK.inv() or
                    Configuration.UI_MODE_TYPE_TELEVISION
            }
            CompositionLocalProvider(LocalConfiguration provides tvConfiguration) {
                CuteFeatureGuideCard(
                    eyebrow = "Guide",
                    title = "Remote focus",
                    body = "The action buttons should own directional focus.",
                    actionLabel = "Next",
                    onAction = { advanced.set(true) },
                    onSkip = {}
                )
            }
        }

        val skipLabel = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(R.string.feature_guide_skip)

        composeTestRule.onNodeWithText("Next").assertIsFocused()
        composeTestRule.onNodeWithText("Next").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.onNodeWithText(skipLabel).assertIsFocused()
        composeTestRule.onNodeWithText(skipLabel).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onNodeWithText("Next").assertIsFocused()
        composeTestRule.onNodeWithText("Next").performKeyInput {
            pressKey(Key.Enter)
        }
        assertTrue(advanced.get())
    }
}
