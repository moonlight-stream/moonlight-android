package com.limelight.utils

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.limelight.HelpActivity
import com.limelight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AppDialogStylerInputTest {
    @get:Rule
    val activityRule = ActivityScenarioRule<HelpActivity>(
        Intent(ApplicationProvider.getApplicationContext(), HelpActivity::class.java)
            .setData(Uri.parse("about:blank"))
    )

    @Test
    fun nonCancelableDialogCanBeClosedWithRemoteBack() {
        assertDialogClosesWith(KeyEvent.KEYCODE_BACK)
    }

    @Test
    fun nonCancelableDialogCanBeClosedWithGamepadB() {
        assertDialogClosesWith(KeyEvent.KEYCODE_BUTTON_B)
    }

    @Test
    fun commonErrorDialogRunsDismissCallbackOnceForGamepadB() {
        val dismissCount = AtomicInteger()
        activityRule.scenario.onActivity { activity ->
            Dialog.displayDialog(
                activity,
                "Dismiss callback test",
                "Closing from a controller must preserve the business callback.",
                Runnable { dismissCount.incrementAndGet() }
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BUTTON_B)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(1, dismissCount.get())
    }

    private fun assertDialogClosesWith(keyCode: Int) {
        lateinit var dialog: AlertDialog
        activityRule.scenario.onActivity { activity ->
            dialog = AlertDialog.Builder(activity, R.style.AppDialogStyle)
                .setTitle("Dismiss input test")
                .setCancelable(false)
                .create()
            dialog.show()
            AppDialogStyler.apply(dialog, activity)
            AppDialogStyler.installDismissKeys(dialog, dismissOnBack = true)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            assertFalse(dialog.isShowing)
        }
    }
}
