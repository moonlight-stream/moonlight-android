package com.limelight.utils

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AboutDialogLauncherPolicyTest {
    @Test
    fun simplifiedChineseUsesCnWebsite() {
        assertEquals(
            "https://www.alkaidlab.cn/",
            AboutDialogLauncher.officialSiteUrl(Locale.SIMPLIFIED_CHINESE)
        )
        assertEquals(
            "https://www.alkaidlab.cn/",
            AboutDialogLauncher.officialSiteUrl(Locale.forLanguageTag("zh-Hans-SG"))
        )
    }

    @Test
    fun otherLocalesUseGlobalWebsite() {
        listOf(
            Locale.ENGLISH,
            Locale.TRADITIONAL_CHINESE,
            Locale.JAPANESE
        ).forEach { locale ->
            assertEquals(
                "https://www.alkaidlab.com/",
                AboutDialogLauncher.officialSiteUrl(locale)
            )
        }
    }

    @Test
    fun ecosystemColumnsAdaptToNarrowTabletAndLandscapeScreens() {
        assertEquals(
            1,
            AboutDialogLauncher.ecosystemColumnCount(
                390,
                Configuration.ORIENTATION_PORTRAIT
            )
        )
        assertEquals(
            2,
            AboutDialogLauncher.ecosystemColumnCount(
                700,
                Configuration.ORIENTATION_PORTRAIT
            )
        )
        assertEquals(
            3,
            AboutDialogLauncher.ecosystemColumnCount(
                960,
                Configuration.ORIENTATION_LANDSCAPE
            )
        )
    }
}
