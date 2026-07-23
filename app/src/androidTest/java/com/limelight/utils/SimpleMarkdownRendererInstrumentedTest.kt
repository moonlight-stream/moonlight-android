package com.limelight.utils

import android.text.Spanned
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleMarkdownRendererInstrumentedTest {

    private val accentColor = 0xFF00AACC.toInt()

    @Test
    fun rendersAbsoluteRelativeAndBareLinks() {
        val rendered = SimpleMarkdownRenderer.render(
            "## [Docs](https://example.com/a_(b_(c)).html)\n\n" +
                "[Issues](../../issues), [Guide](../../docs/指南), https://example.com/path_(x).",
            accentColor,
            "https://github.com/qiin2333/moonlight-vplus/releases/tag/v12.10.8"
        )

        val spans = urlSpans(rendered)
        assertEquals(4, spans.size)
        assertTrue(spans.any { it.url == "https://example.com/a_(b_(c)).html" })
        assertTrue(spans.any { it.url == "https://github.com/qiin2333/moonlight-vplus/issues" })
        assertTrue(spans.any { it.url == "https://github.com/qiin2333/moonlight-vplus/docs/%E6%8C%87%E5%8D%97" })
        assertTrue(spans.any { it.url == "https://example.com/path_(x)" })
    }

    @Test
    fun preservesUnmatchedBoldMarkerAndContinuesParsing() {
        val rendered = SimpleMarkdownRenderer.render(
            "**unmatched [Issues](https://example.com/issues) https://example.com/path",
            accentColor
        )

        assertTrue(rendered.toString().startsWith("**unmatched"))
        assertEquals(2, urlSpans(rendered).size)
    }

    @Test
    fun rejectsInvalidTargetsAndSupportsInertLinks() {
        val invalid = SimpleMarkdownRenderer.render(
            "[FTP](ftp://example.com) [Relative](../../issues) javascript:bad https://",
            accentColor
        )
        assertEquals(0, urlSpans(invalid).size)

        val inert = SimpleMarkdownRenderer.render(
            "[Issues](https://example.com/issues) https://example.com/path",
            accentColor,
            "https://github.com/qiin2333/moonlight-vplus/releases/tag/v12.10.8",
            linksEnabled = false
        )
        assertEquals(0, urlSpans(inert).size)
    }

    private fun urlSpans(value: CharSequence): Array<URLSpan> {
        val spanned = value as Spanned
        return spanned.getSpans(0, spanned.length, URLSpan::class.java)
    }
}
