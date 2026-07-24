package com.limelight.utils

import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.view.View
import java.net.URI

/**
 * 将 GitHub release body 的 Markdown 渲染为少女清新手账风 SpannableString。
 * 标题用 ┃ 竖线装饰如手账 washi tape，列表用 ◦ 空心圆，分段用花朵点缀。
 */
object SimpleMarkdownRenderer {

    private const val BULLET_SYMBOL = "◦ "
    private const val SECTION_DIVIDER = "· · · ✿ · · ·"

    fun render(
        markdown: String?,
        accentColor: Int,
        baseUrl: String? = null,
        linksEnabled: Boolean = true,
        onLink: ((String) -> Unit)? = null
    ): CharSequence {
        if (markdown.isNullOrEmpty()) return ""

        val builder = SpannableStringBuilder()
        val lines = markdown.split("\n")
        var previousWasEmpty = false
        var hadContent = false

        for (rawLine in lines) {
            val line = rawLine.trim()

            if (line.isEmpty()) {
                if (builder.isNotEmpty() && !previousWasEmpty) {
                    builder.append("\n")
                }
                previousWasEmpty = true
                continue
            }
            previousWasEmpty = false

            when {
                line.startsWith("###") -> {
                    if (hadContent) appendDivider(builder, accentColor)
                    val header = line.replaceFirst("^#{1,6}\\s*".toRegex(), "")
                    appendHeader(
                        builder,
                        processInlineStyles(
                            header,
                            accentColor,
                            baseUrl,
                            linksEnabled,
                            onLink
                        ),
                        1.0f,
                        accentColor
                    )
                }
                line.startsWith("##") -> {
                    if (hadContent) appendDivider(builder, accentColor)
                    val header = line.replaceFirst("^#{1,6}\\s*".toRegex(), "")
                    appendHeader(
                        builder,
                        processInlineStyles(
                            header,
                            accentColor,
                            baseUrl,
                            linksEnabled,
                            onLink
                        ),
                        1.1f,
                        accentColor
                    )
                }
                line.startsWith("#") -> {
                    if (hadContent) appendDivider(builder, accentColor)
                    val header = line.replaceFirst("^#{1,6}\\s*".toRegex(), "")
                    appendHeader(
                        builder,
                        processInlineStyles(
                            header,
                            accentColor,
                            baseUrl,
                            linksEnabled,
                            onLink
                        ),
                        1.2f,
                        accentColor
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    appendBullet(
                        builder,
                        processInlineStyles(
                            line.substring(2).trim(),
                            accentColor,
                            baseUrl,
                            linksEnabled,
                            onLink
                        ),
                        accentColor
                    )
                }
                else -> {
                    if (builder.isNotEmpty()) builder.append("\n")
                    builder.append(
                        processInlineStyles(
                            line,
                            accentColor,
                            baseUrl,
                            linksEnabled,
                            onLink
                        )
                    )
                }
            }
            hadContent = true
        }

        // 去除尾部空行
        while (builder.isNotEmpty() && builder[builder.length - 1] == '\n') {
            builder.delete(builder.length - 1, builder.length)
        }

        return builder
    }

    private fun appendHeader(
        builder: SpannableStringBuilder,
        text: CharSequence,
        sizeMultiplier: Float,
        color: Int
    ) {
        if (builder.isNotEmpty()) builder.append("\n")

        val start = builder.length
        builder.append("┃")
        val textStart = builder.length
        builder.append(text)
        val end = builder.length

        builder.setSpan(
            ForegroundColorSpan(color),
            start,
            start + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            textStart,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            RelativeSizeSpan(sizeMultiplier),
            textStart,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            ForegroundColorSpan(color),
            textStart,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.append("\n")
    }

    private fun appendDivider(builder: SpannableStringBuilder, color: Int) {
        if (builder.isNotEmpty() && builder[builder.length - 1] != '\n') {
            builder.append("\n")
        }
        val start = builder.length
        builder.append(SECTION_DIVIDER)
        val end = builder.length
        builder.setSpan(
            ForegroundColorSpan(color and 0x55FFFFFF or 0x55000000),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            RelativeSizeSpan(0.8f),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.append("\n")
    }

    private fun appendBullet(
        builder: SpannableStringBuilder,
        text: CharSequence,
        accentColor: Int
    ) {
        if (builder.isNotEmpty() && builder[builder.length - 1] != '\n') {
            builder.append("\n")
        }
        val start = builder.length

        val symbolStart = builder.length
        builder.append(BULLET_SYMBOL)
        val symbolEnd = builder.length
        builder.setSpan(
            ForegroundColorSpan(accentColor),
            symbolStart,
            symbolEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        builder.append(text)
        builder.append("\n")
        val end = builder.length
        builder.setSpan(
            LeadingMarginSpan.Standard(16, 32),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /**
     * Renders the inline subset used by GitHub release notes.
     *
     * Besides bold text, this creates URLSpan instances for Markdown links and
     * plain http(s) URLs. The TextView displaying the result must install a
     * LinkMovementMethod for those spans to receive clicks.
     */
    private fun processInlineStyles(
        text: String,
        linkColor: Int,
        baseUrl: String?,
        linksEnabled: Boolean,
        onLink: ((String) -> Unit)?
    ): CharSequence {
        val result = SpannableStringBuilder()
        appendInlineContent(result, text, linkColor, baseUrl, linksEnabled, onLink)
        return result
    }

    private fun appendInlineContent(
        result: SpannableStringBuilder,
        text: String,
        linkColor: Int,
        baseUrl: String?,
        linksEnabled: Boolean,
        onLink: ((String) -> Unit)?
    ) {
        var i = 0
        while (i < text.length) {
            val markdownLink = if (linksEnabled) {
                parseMarkdownLinkAt(text, i, baseUrl)
            } else {
                null
            }
            if (markdownLink != null) {
                val linkStart = result.length
                result.append(markdownLink.label)
                addLinkSpans(
                    result,
                    linkStart,
                    result.length,
                    markdownLink.url,
                    linkColor,
                    onLink
                )
                i = markdownLink.endIndex
                continue
            }

            if (text.startsWith("**", i)) {
                val boldEnd = text.indexOf("**", i + 2)
                if (boldEnd == -1) {
                    result.append(text, i, i + 2)
                    i += 2
                    continue
                }

                val spanStart = result.length
                appendInlineContent(
                    result,
                    text.substring(i + 2, boldEnd),
                    linkColor,
                    baseUrl,
                    linksEnabled,
                    onLink
                )
                result.setSpan(
                    StyleSpan(Typeface.BOLD),
                    spanStart,
                    result.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                i = boldEnd + 2
                continue
            }

            val bareUrlEnd = if (linksEnabled) findBareUrlEnd(text, i) else null
            if (bareUrlEnd != null) {
                val linkStart = result.length
                result.append(text, i, bareUrlEnd)
                addLinkSpans(
                    result,
                    linkStart,
                    result.length,
                    text.substring(i, bareUrlEnd),
                    linkColor,
                    onLink
                )
                i = bareUrlEnd
                continue
            }

            result.append(text[i])
            i++
        }
    }

    private fun addLinkSpans(
        result: SpannableStringBuilder,
        start: Int,
        end: Int,
        url: String,
        linkColor: Int,
        onLink: ((String) -> Unit)?
    ) {
        val urlSpan = if (onLink == null) {
            URLSpan(url)
        } else {
            object : URLSpan(url) {
                override fun onClick(widget: View) {
                    onLink(url)
                }
            }
        }
        result.setSpan(urlSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(
            ForegroundColorSpan(linkColor),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun parseMarkdownLinkAt(
        text: String,
        start: Int,
        baseUrl: String?
    ): MarkdownLink? {
        if (start >= text.length || text[start] != '[') return null

        val labelEnd = text.indexOf(']', start + 1)
        if (labelEnd <= start + 1 ||
            labelEnd + 1 >= text.length ||
            text[labelEnd + 1] != '('
        ) {
            return null
        }

        var depth = 1
        var cursor = labelEnd + 2
        while (cursor < text.length) {
            when (text[cursor]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            cursor++
        }
        if (cursor >= text.length || depth != 0) return null

        var destination = text.substring(labelEnd + 2, cursor).trim()
        if (destination.startsWith("<") && destination.endsWith(">")) {
            destination = destination.substring(1, destination.length - 1).trim()
        } else {
            val titleStart = destination.indexOfFirst { it.isWhitespace() }
            if (titleStart >= 0) destination = destination.substring(0, titleStart)
        }

        val resolvedUrl = resolveHttpUrl(destination, baseUrl) ?: return null
        return MarkdownLink(
            text.substring(start + 1, labelEnd),
            resolvedUrl,
            cursor + 1
        )
    }

    private fun findBareUrlEnd(text: String, start: Int): Int? {
        if (!text.startsWith("https://", start) &&
            !text.startsWith("http://", start)
        ) {
            return null
        }

        var end = start
        while (end < text.length &&
            !text[end].isWhitespace() &&
            text[end] !in "<[]>\"'"
        ) {
            end++
        }

        while (end > start && text[end - 1] in ".,!?;:") end--
        if (end > start && text[end - 1] == ')') {
            val candidate = text.substring(start, end)
            if (candidate.count { it == '(' } < candidate.count { it == ')' }) {
                end--
            }
        }

        val url = text.substring(start, end)
        return if (end > start && resolveHttpUrl(url, null) != null) end else null
    }

    private fun resolveHttpUrl(value: String, baseUrl: String?): String? {
        return try {
            val directUri = Uri.parse(value)
            if (isHttpUrl(directUri)) {
                return directUri.toString()
            }

            if (baseUrl.isNullOrBlank()) return null

            // Uri.parse accepts the relative destinations used by GitHub notes;
            // encode only characters that java.net.URI.resolve rejects.
            val normalizedDestination = Uri.encode(
                value,
                "/?#@&=+$,;:-._~!()'[]%"
            )
            val resolved = URI(baseUrl).resolve(normalizedDestination)
            val resolvedUri = Uri.parse(resolved.toString())
            resolved.toString().takeIf { isHttpUrl(resolvedUri) }
        } catch (_: Exception) {
            null
        }
    }

    private fun isHttpUrl(uri: Uri): Boolean {
        return (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }

    private data class MarkdownLink(
        val label: String,
        val url: String,
        val endIndex: Int
    )
}
