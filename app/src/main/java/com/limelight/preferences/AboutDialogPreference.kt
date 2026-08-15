@file:Suppress("DEPRECATION")

package com.limelight.preferences

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import com.limelight.utils.AboutDialogLauncher

class AboutDialogPreference : Preference {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    @Deprecated("Deprecated in Java")
    override fun onClick() {
        AboutDialogLauncher.show(context)
    }
}
