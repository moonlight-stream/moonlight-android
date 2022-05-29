package com.limelight.utils

import android.app.Activity
import android.content.ComponentName
import android.util.Log

object SamsungDexUtils {
    val TAG = this::class.java.name

    fun dexMetaKeyCapture(activity: Activity) {
        runCatching {
            val semWindowManager = Class.forName("com.samsung.android.view.SemWindowManager")
            val getInstanceMethod = semWindowManager.getMethod("getInstance")
            val manager = getInstanceMethod.invoke(null)

            val requestMetaKeyEvent = semWindowManager.getDeclaredMethod(
                    "requestMetaKeyEvent", ComponentName::class.java, Boolean::class.java)
            requestMetaKeyEvent.invoke(manager, activity.componentName, true)
        }.onFailure {
            Log.d(TAG, "Could not call com.samsung.android.view.SemWindowManager.requestMetaKeyEvent ", it)
        }
    }
}