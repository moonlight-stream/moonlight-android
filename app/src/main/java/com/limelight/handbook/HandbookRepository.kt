package com.limelight.handbook

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import com.limelight.LimeLog
import com.limelight.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed class HandbookLoadResult {
    data class Success(
        val html: String,
        val baseUrl: String
    ) : HandbookLoadResult()

    data class Failure(val reason: HandbookFailureReason) : HandbookLoadResult()
}

enum class HandbookFailureReason {
    NETWORK,
    TIMEOUT,
    UNAVAILABLE
}

class HandbookRepository(
    private val appContext: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(ORIGIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ORIGIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
) {
    private val cache = HandbookCache(appContext)

    suspend fun load(page: HandbookPageRef): HandbookLoadResult = withContext(Dispatchers.IO) {
        val cacheStartedAt = SystemClock.elapsedRealtime()
        cache.get(page)?.let { cached ->
            LimeLog.info(
                "Handbook cache loaded in " +
                    "${SystemClock.elapsedRealtime() - cacheStartedAt} ms"
            )
            return@withContext applyPresentation(cached)
        }

        if (!isNetworkConnected()) {
            return@withContext HandbookLoadResult.Failure(HandbookFailureReason.NETWORK)
        }

        val failures = mutableListOf<AttemptFailure>()
        for ((sourceIndex, candidate) in HandbookUrlPolicy.originCandidates(page).withIndex()) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val result = fetchFromOrigin(candidate)
                LimeLog.info(
                    "Handbook source ${sourceIndex + 1} loaded in " +
                        "${SystemClock.elapsedRealtime() - startedAt} ms"
                )
                cache.put(page, result)
                return@withContext applyPresentation(result)
            } catch (error: Exception) {
                val failure = classify(error)
                failures += failure
                LimeLog.warning(
                    "Handbook source ${sourceIndex + 1} failed after " +
                        "${SystemClock.elapsedRealtime() - startedAt} ms ($failure)"
                )
            }
        }

        val reason = when {
            failures.isNotEmpty() && failures.all { it == AttemptFailure.NETWORK } ->
                HandbookFailureReason.NETWORK
            failures.isNotEmpty() && failures.all { it == AttemptFailure.TIMEOUT } ->
                HandbookFailureReason.TIMEOUT
            else -> HandbookFailureReason.UNAVAILABLE
        }
        HandbookLoadResult.Failure(reason)
    }

    private fun fetchFromOrigin(initialUrl: HttpUrl): HandbookLoadResult.Success {
        val deadlineNanos = System.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(ORIGIN_TIMEOUT_MS)
        var completedAttempts = 0

        while (true) {
            completedAttempts++
            try {
                return fetchOriginAttempt(initialUrl, deadlineNanos)
            } catch (error: IOException) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (!HandbookRetryPolicy.shouldRetry(
                        error,
                        completedAttempts,
                        remainingNanos
                    )
                ) {
                    throw error
                }

                // A server can close an idle keep-alive connection without the
                // client noticing. Drop pooled connections before the one
                // explicit retry so the next request establishes a fresh path.
                client.connectionPool.evictAll()
            }
        }
    }

    private fun fetchOriginAttempt(
        initialUrl: HttpUrl,
        deadlineNanos: Long
    ): HandbookLoadResult.Success {
        val originHost = initialUrl.host
        var currentUrl = initialUrl
        var redirectCount = 0

        while (true) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos < TimeUnit.MILLISECONDS.toNanos(1L)) {
                throw SocketTimeoutException("Handbook origin timed out")
            }

            val requestClient = client.newBuilder()
                .callTimeout(remainingNanos, TimeUnit.NANOSECONDS)
                .build()
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", "text/html, application/xhtml+xml")
                .header("Cache-Control", "no-cache")
                .build()

            val response = requestClient.newCall(request).execute()
            if (response.code in REDIRECT_CODES) {
                response.use {
                    if (++redirectCount > MAX_REDIRECTS) {
                        throw PermanentHandbookException("Too many handbook redirects")
                    }
                    currentUrl = validatedRedirect(it, currentUrl, originHost)
                }
                continue
            }

            response.use {
                if (!it.isSuccessful) {
                    if (it.code in RETRYABLE_HTTP_CODES) {
                        throw IOException("Handbook response was temporarily unavailable")
                    }
                    throw PermanentHandbookException(
                        "Handbook response was not successful"
                    )
                }

                val html = readValidatedHtml(it)
                return HandbookLoadResult.Success(
                    html = html,
                    baseUrl = currentUrl.toString()
                )
            }
        }
    }

    private fun validatedRedirect(
        response: Response,
        currentUrl: HttpUrl,
        originHost: String
    ): HttpUrl {
        val location = response.header("Location")
            ?: throw PermanentHandbookException("Handbook redirect had no location")
        val redirected = currentUrl.resolve(location)
            ?: throw PermanentHandbookException("Invalid handbook redirect")
        if (redirected.host != originHost || HandbookUrlPolicy.parse(redirected.toString()) == null) {
            throw PermanentHandbookException("Handbook redirect left its allowed origin")
        }
        return redirected.newBuilder().fragment(null).build()
    }

    private fun readValidatedHtml(response: Response): String {
        val body = response.body
        val contentType = body.contentType()
            ?: throw PermanentHandbookException("Handbook response had no content type")
        val isHtml = (contentType.type == "text" && contentType.subtype == "html") ||
            (contentType.type == "application" && contentType.subtype == "xhtml+xml")
        if (!isHtml) {
            throw PermanentHandbookException("Handbook response was not HTML")
        }
        if (body.contentLength() > MAX_HTML_BYTES) {
            throw PermanentHandbookException("Handbook response was too large")
        }

        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_HTML_BYTES) {
                    throw PermanentHandbookException("Handbook response was too large")
                }
                output.write(buffer, 0, read)
            }
        }

        val charset = contentType.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
        return output.toString(charset.name())
    }

    private fun applyPresentation(
        content: HandbookLoadResult.Success
    ): HandbookLoadResult.Success {
        val localizedTitle = TextUtils.htmlEncode(
            appContext.getString(R.string.handbook_document_center_title)
        )
        val headingMatch = DOCUMENT_CENTER_HEADING.find(content.html)
        val localizedHtml = headingMatch?.let { match ->
            content.html.replaceRange(
                match.range,
                match.groupValues[1] + localizedTitle + match.groupValues[2]
            )
        } ?: content.html
        val style = """
            <style id="moonlight-handbook-presentation">
              header.site-header { display: none !important; }
            </style>
        """.trimIndent()
        val headEnd = localizedHtml.indexOf("</head>", ignoreCase = true)
        val presentedHtml = if (headEnd >= 0) {
            localizedHtml.substring(0, headEnd) +
                style +
                localizedHtml.substring(headEnd)
        } else {
            style + localizedHtml
        }
        return content.copy(html = presentedHtml)
    }

    @Suppress("DEPRECATION")
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    private fun classify(error: Exception): AttemptFailure {
        return when (error) {
            is SocketTimeoutException,
            is InterruptedIOException -> AttemptFailure.TIMEOUT
            is UnknownHostException,
            is NoRouteToHostException,
            is ConnectException -> AttemptFailure.NETWORK
            else -> AttemptFailure.UNAVAILABLE
        }
    }

    private enum class AttemptFailure {
        NETWORK,
        TIMEOUT,
        UNAVAILABLE
    }

    private companion object {
        const val ORIGIN_TIMEOUT_MS = 3_000L
        const val MAX_HTML_BYTES = 2 * 1024 * 1024
        const val READ_BUFFER_BYTES = 8 * 1024
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val RETRYABLE_HTTP_CODES = setOf(408, 421, 425, 500, 502, 503, 504)
        val DOCUMENT_CENTER_HEADING = Regex(
            """(<h1\b[^>]*>)\s*文档中心\s*(</h1\s*>)""",
            RegexOption.IGNORE_CASE
        )
    }
}

internal class PermanentHandbookException(message: String) : IOException(message)

internal object HandbookRetryPolicy {
    const val MAX_ATTEMPTS = 2
    private val MIN_RETRY_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(250L)

    fun shouldRetry(
        error: IOException,
        completedAttempts: Int,
        remainingNanos: Long
    ): Boolean {
        return completedAttempts < MAX_ATTEMPTS &&
            remainingNanos >= MIN_RETRY_BUDGET_NANOS &&
            error !is PermanentHandbookException &&
            error !is SSLException
    }
}
