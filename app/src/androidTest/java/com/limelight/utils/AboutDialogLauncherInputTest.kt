package com.limelight.utils

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.limelight.HelpActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutDialogLauncherInputTest {
    @get:Rule
    val activityRule = ActivityScenarioRule<HelpActivity>(
        Intent(ApplicationProvider.getApplicationContext(), HelpActivity::class.java)
            .setData(Uri.parse("about:blank"))
    )

    @After
    fun releaseDialogs() {
        activityRule.scenario.onActivity(AboutDialogLauncher::release)
    }

    @Test
    fun gamepadBackAndEscapeDismissDialog() {
        lateinit var dialog: Dialog
        activityRule.scenario.onActivity { dialog = AboutDialogLauncher.show(it) }
        waitForIdle()

        sendKey(KeyEvent.KEYCODE_BUTTON_B)
        activityRule.scenario.onActivity { assertFalse(dialog.isShowing) }

        activityRule.scenario.onActivity { dialog = AboutDialogLauncher.show(it) }
        waitForIdle()
        sendKey(KeyEvent.KEYCODE_BACK)
        activityRule.scenario.onActivity { assertFalse(dialog.isShowing) }

        activityRule.scenario.onActivity { dialog = AboutDialogLauncher.show(it) }
        waitForIdle()
        sendKey(KeyEvent.KEYCODE_ESCAPE)
        activityRule.scenario.onActivity { assertFalse(dialog.isShowing) }
    }

    @Test
    fun controllerOpensEcosystemAndReturnsToAboutDialog() {
        lateinit var dialog: Dialog
        activityRule.scenario.onActivity { dialog = AboutDialogLauncher.show(it) }
        waitForIdle()

        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        sendKey(KeyEvent.KEYCODE_BUTTON_A)
        sendKey(KeyEvent.KEYCODE_BUTTON_B)

        activityRule.scenario.onActivity { assertTrue(dialog.isShowing) }
        sendKey(KeyEvent.KEYCODE_ESCAPE)
        activityRule.scenario.onActivity { assertFalse(dialog.isShowing) }
    }

    @Test
    fun nonOwnerCannotRecreateOrReleaseDialog() {
        lateinit var dialog: Dialog
        lateinit var changedConfiguration: Configuration
        activityRule.scenario.onActivity { activity ->
            dialog = AboutDialogLauncher.show(activity)
            changedConfiguration = Configuration(activity.resources.configuration).apply {
                orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Configuration.ORIENTATION_PORTRAIT
                } else {
                    Configuration.ORIENTATION_LANDSCAPE
                }
            }
        }
        waitForIdle()

        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AboutDialogLauncher.onConfigurationChanged(applicationContext, changedConfiguration)
            AboutDialogLauncher.release(applicationContext)
        }
        waitForIdle()

        activityRule.scenario.onActivity { activity ->
            assertTrue(dialog.isShowing)
            AboutDialogLauncher.release(activity)
            assertFalse(dialog.isShowing)
        }
    }

    @Test
    fun showingEcosystemAgainDismissesPreviousDialog() {
        lateinit var first: Dialog
        lateinit var second: Dialog
        activityRule.scenario.onActivity { activity ->
            first = AboutDialogLauncher.showEcosystemDialog(activity)
            second = AboutDialogLauncher.showEcosystemDialog(activity)
        }
        waitForIdle()

        activityRule.scenario.onActivity {
            assertFalse(first.isShowing)
            assertTrue(second.isShowing)
        }
    }

    @Test
    fun ownerConfigurationChangeRecreatesDialogsAndRestoresFocus() {
        lateinit var original: AboutDialogLauncher.DialogSnapshot
        activityRule.scenario.onActivity { AboutDialogLauncher.show(it) }
        waitForIdle()
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        sendKey(KeyEvent.KEYCODE_BUTTON_A)
        original = requireNotNull(AboutDialogLauncher.dialogSnapshot())

        activityRule.scenario.onActivity { activity ->
            val changedConfiguration = Configuration(activity.resources.configuration).apply {
                orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Configuration.ORIENTATION_PORTRAIT
                } else {
                    Configuration.ORIENTATION_LANDSCAPE
                }
            }
            AboutDialogLauncher.onConfigurationChanged(activity, changedConfiguration)
        }
        waitForIdle()

        val recreated = requireNotNull(AboutDialogLauncher.dialogSnapshot())
        assertNotSame(original.main, recreated.main)
        assertNotSame(original.ecosystem, recreated.ecosystem)
        assertFalse(requireNotNull(original.main).isShowing)
        assertFalse(requireNotNull(original.ecosystem).isShowing)
        assertTrue(requireNotNull(recreated.main).isShowing)
        assertTrue(requireNotNull(recreated.ecosystem).isShowing)
        assertEquals(1, recreated.mainFocusIndex)
        assertEquals(0, recreated.ecosystemFocusIndex)

        sendKey(KeyEvent.KEYCODE_BUTTON_B)
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(2, AboutDialogLauncher.dialogSnapshot()?.mainFocusIndex)
    }

    private fun sendKey(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        waitForIdle()
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

}