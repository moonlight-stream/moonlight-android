package com.limelight.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.limelight.HelpActivity
import com.limelight.R
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutDialogInputTest {
    @get:Rule
    val activityRule = ActivityScenarioRule<HelpActivity>(
        Intent(ApplicationProvider.getApplicationContext(), HelpActivity::class.java)
            .setData(Uri.parse("about:blank"))
    )

    @After
    fun releaseDialogs() {
        activityRule.scenario.onActivity { activity ->
            AboutDialogLauncher.release(activity)
        }
    }

    @Test
    fun controllerCanOpenAndCloseEcosystemWithoutClosingAboutDialog() {
        lateinit var aboutDialog: AlertDialog
        activityRule.scenario.onActivity { activity ->
            aboutDialog = AboutDialogLauncher.show(activity)
        }
        waitForIdle()

        activityRule.scenario.onActivity {
            assertTrue(aboutDialog.findViewById<View>(R.id.about_handbook_button).hasFocus())
        }

        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        activityRule.scenario.onActivity {
            assertTrue(aboutDialog.findViewById<View>(R.id.about_ecosystem_button).hasFocus())
        }

        sendKey(KeyEvent.KEYCODE_BUTTON_A)
        sendKey(KeyEvent.KEYCODE_BUTTON_B)
        activityRule.scenario.onActivity {
            assertTrue(aboutDialog.isShowing)
            assertTrue(aboutDialog.findViewById<View>(R.id.about_ecosystem_button).hasFocus())
        }

        sendKey(KeyEvent.KEYCODE_ESCAPE)
        activityRule.scenario.onActivity {
            assertFalse(aboutDialog.isShowing)
        }
    }

    @Test
    fun externalActionsStayVisibleAndReachableByDirectionalNavigation() {
        lateinit var aboutDialog: AlertDialog
        activityRule.scenario.onActivity { activity ->
            aboutDialog = AboutDialogLauncher.show(activity)
        }
        waitForIdle()

        activityRule.scenario.onActivity {
            listOf(
                R.id.about_star_button,
                R.id.about_github_button,
                R.id.about_qq_button,
                R.id.about_site_button
            ).forEach { actionId ->
                assertTrue(aboutDialog.findViewById<View>(actionId).isShown)
            }
        }

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        activityRule.scenario.onActivity {
            assertTrue(aboutDialog.findViewById<View>(R.id.about_github_button).hasFocus())
        }

        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        activityRule.scenario.onActivity {
            assertTrue(aboutDialog.findViewById<View>(R.id.about_qq_button).hasFocus())
        }

        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        activityRule.scenario.onActivity {
            assertTrue(aboutDialog.findViewById<View>(R.id.about_site_button).hasFocus())
        }
    }

    @Test
    fun nonOwnerCannotRecreateOrReleaseDialog() {
        lateinit var aboutDialog: AlertDialog
        lateinit var changedConfiguration: Configuration
        activityRule.scenario.onActivity { activity ->
            aboutDialog = AboutDialogLauncher.show(activity)
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
        AboutDialogLauncher.onConfigurationChanged(applicationContext, changedConfiguration)
        AboutDialogLauncher.release(applicationContext)
        waitForIdle()

        activityRule.scenario.onActivity { activity ->
            assertTrue(aboutDialog.isShowing)
            AboutDialogLauncher.release(activity)
            assertFalse(aboutDialog.isShowing)
        }
    }

    @Test
    fun invalidInitialFocusFallsBackToCloseButton() {
        lateinit var ecosystemDialog: AlertDialog
        activityRule.scenario.onActivity { activity ->
            ecosystemDialog = AboutDialogLauncher.showEcosystemDialog(
                activity,
                initialFocusIndex = -1
            )
        }
        waitForIdle()

        activityRule.scenario.onActivity {
            assertTrue(
                ecosystemDialog.findViewById<View>(R.id.about_ecosystem_close_button).hasFocus()
            )
        }
    }

    @Test
    fun directionalNavigationWorksInResponsiveEcosystemLayout() {
        lateinit var ecosystemDialog: AlertDialog
        var columnCount = 1
        var isLandscape = false
        activityRule.scenario.onActivity { activity ->
            isLandscape = activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            columnCount = AboutDialogLauncher.ecosystemColumnCount(
                activity.resources.configuration.screenWidthDp,
                activity.resources.configuration.orientation
            )
            ecosystemDialog = AboutDialogLauncher.showEcosystemDialog(activity)
        }
        waitForIdle()

        activityRule.scenario.onActivity {
            if (isLandscape) {
                val menu = ecosystemDialog.findViewById<LinearLayout>(R.id.about_ecosystem_menu)
                assertTrue(menu.getChildAt(0).hasFocus())
            } else {
                val grid = ecosystemDialog.findViewById<GridLayout>(R.id.about_ecosystem_grid)
                assertTrue(grid.getChildAt(0).hasFocus())
            }
        }

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        activityRule.scenario.onActivity {
            if (isLandscape) {
                val menu = ecosystemDialog.findViewById<LinearLayout>(R.id.about_ecosystem_menu)
                assertTrue(menu.getChildAt(1).hasFocus())
            } else {
                val grid = ecosystemDialog.findViewById<GridLayout>(R.id.about_ecosystem_grid)
                assertTrue(grid.getChildAt(columnCount).hasFocus())
            }
        }

        if (isLandscape) {
            sendKey(KeyEvent.KEYCODE_BUTTON_A)
            activityRule.scenario.onActivity {
                assertTrue(
                    ecosystemDialog.findViewById<View>(R.id.about_ecosystem_open_button).hasFocus()
                )
            }
        } else {
            repeat(6) { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            activityRule.scenario.onActivity {
                val scroll = ecosystemDialog.findViewById<ScrollView>(R.id.about_ecosystem_scroll)
                val scrollbar = ecosystemDialog.findViewById<View>(R.id.about_ecosystem_scrollbar)
                if (scroll.canScrollVertically(-1) || scroll.canScrollVertically(1)) {
                    assertTrue(scroll.scrollY > 0)
                    assertTrue(scrollbar.visibility == View.VISIBLE)
                    assertTrue(scrollbar.height > 0)
                }
            }
        }

        sendKey(KeyEvent.KEYCODE_BACK)
        activityRule.scenario.onActivity {
            assertFalse(ecosystemDialog.isShowing)
        }
    }

    private fun sendKey(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        waitForIdle()
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
