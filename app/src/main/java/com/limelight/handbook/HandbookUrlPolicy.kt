package com.limelight.handbook

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A handbook location without an origin. Keeping the origin out of navigation
 * state lets every page use the same international-first fallback policy.
 */
data class HandbookPageRef(
    val encodedPath: String,
    val encodedQuery: String? = null,
    val encodedFragment: String? = null
)

object HandbookUrlPolicy {
    const val PRIMARY_HOST = "www.alkaidlab.com"
    const val FALLBACK_HOST = "www.alkaidlab.cn"

    private const val DOCS_ROOT = "/docs/"
    private val allowedHosts = setOf(PRIMARY_HOST, FALLBACK_HOST)

    val index: HandbookPageRef = HandbookPageRef(DOCS_ROOT)

    fun parse(url: String?): HandbookPageRef? {
        val parsed = url?.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https" ||
            parsed.host !in allowedHosts ||
            parsed.port != 443 ||
            parsed.username.isNotEmpty() ||
            parsed.password.isNotEmpty()
        ) {
            return null
        }

        val path = parsed.encodedPath
        if (path != DOCS_ROOT && !path.startsWith(DOCS_ROOT)) {
            return null
        }

        return HandbookPageRef(
            encodedPath = path,
            encodedQuery = parsed.encodedQuery,
            encodedFragment = parsed.encodedFragment
        )
    }

    fun originCandidates(page: HandbookPageRef): List<HttpUrl> {
        require(isValidPageRef(page)) { "Invalid handbook page reference" }
        return listOf(PRIMARY_HOST, FALLBACK_HOST).map { host ->
            buildUrl(host, page, includeFragment = false)
        }
    }

    fun canonicalUrl(page: HandbookPageRef): String {
        require(isValidPageRef(page)) { "Invalid handbook page reference" }
        return buildUrl(PRIMARY_HOST, page, includeFragment = true).toString()
    }

    fun isExternalHttps(url: String?): Boolean {
        val parsed = url?.toHttpUrlOrNull() ?: return false
        return parsed.scheme == "https" &&
            parsed.host.isNotBlank() &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty() &&
            parse(url) == null
    }

    private fun isValidPageRef(page: HandbookPageRef): Boolean {
        if (page.encodedPath != DOCS_ROOT && !page.encodedPath.startsWith(DOCS_ROOT)) {
            return false
        }

        // Reparse a constructed URL so encoded dot segments and malformed
        // components cannot be introduced through an Intent extra.
        return runCatching {
            val url = buildUrl(PRIMARY_HOST, page, includeFragment = true)
            url.encodedPath == page.encodedPath &&
                url.encodedQuery == page.encodedQuery &&
                url.encodedFragment == page.encodedFragment
        }.getOrDefault(false)
    }

    private fun buildUrl(
        host: String,
        page: HandbookPageRef,
        includeFragment: Boolean
    ): HttpUrl {
        return HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .encodedPath(page.encodedPath)
            .apply {
                page.encodedQuery?.let(::encodedQuery)
                if (includeFragment) {
                    page.encodedFragment?.let(::encodedFragment)
                }
            }
            .build()
    }
}
