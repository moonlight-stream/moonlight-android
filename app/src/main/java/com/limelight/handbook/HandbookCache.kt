package com.limelight.handbook

import android.content.Context
import android.util.AtomicFile
import com.limelight.LimeLog
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class HandbookCache(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val directory = File(context.cacheDir, CACHE_DIRECTORY)

    fun get(page: HandbookPageRef): HandbookLoadResult.Success? {
        val identity = cacheIdentity(page) ?: return null
        val cacheFile = AtomicFile(File(directory, "${sha256(identity)}.bin"))

        return try {
            DataInputStream(BufferedInputStream(cacheFile.openRead())).use { input ->
                if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_VERSION) {
                    throw IOException("Unsupported handbook cache format")
                }

                val cachedAtMillis = input.readLong()
                val cachedIdentity = input.readUtf8(MAX_URL_BYTES)
                val baseUrl = input.readUtf8(MAX_URL_BYTES)
                val ageMillis = currentTimeMillis() - cachedAtMillis
                if (cachedIdentity != identity ||
                    HandbookUrlPolicy.parse(baseUrl) == null ||
                    ageMillis < 0L ||
                    ageMillis >= CACHE_TTL_MS
                ) {
                    cacheFile.delete()
                    return null
                }

                val html = input.readUtf8(MAX_CACHED_HTML_BYTES)
                if (input.read() != -1) {
                    throw IOException("Unexpected handbook cache data")
                }
                HandbookLoadResult.Success(html = html, baseUrl = baseUrl)
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (error: Exception) {
            cacheFile.delete()
            LimeLog.warning("Handbook cache read failed (${error.javaClass.simpleName})")
            null
        }
    }

    fun put(page: HandbookPageRef, content: HandbookLoadResult.Success) {
        val identity = cacheIdentity(page) ?: return
        if (HandbookUrlPolicy.parse(content.baseUrl) == null) return

        val identityBytes = identity.toByteArray(StandardCharsets.UTF_8)
        val baseUrlBytes = content.baseUrl.toByteArray(StandardCharsets.UTF_8)
        val htmlBytes = content.html.toByteArray(StandardCharsets.UTF_8)
        if (identityBytes.size > MAX_URL_BYTES ||
            baseUrlBytes.size > MAX_URL_BYTES ||
            htmlBytes.size > MAX_CACHED_HTML_BYTES
        ) {
            return
        }

        if (!directory.isDirectory && !directory.mkdirs()) {
            LimeLog.warning("Handbook cache directory is unavailable")
            return
        }

        val cacheFile = AtomicFile(File(directory, "${sha256(identity)}.bin"))
        var fileOutput: java.io.FileOutputStream? = null
        try {
            val stream = cacheFile.startWrite()
            fileOutput = stream
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(CACHE_MAGIC)
            output.writeInt(CACHE_VERSION)
            output.writeLong(currentTimeMillis())
            output.writeUtf8(identityBytes)
            output.writeUtf8(baseUrlBytes)
            output.writeUtf8(htmlBytes)
            output.flush()
            cacheFile.finishWrite(stream)
            fileOutput = null
        } catch (error: Exception) {
            fileOutput?.let { runCatching { cacheFile.failWrite(it) } }
            LimeLog.warning("Handbook cache write failed (${error.javaClass.simpleName})")
        }
    }

    private fun cacheIdentity(page: HandbookPageRef): String? {
        return runCatching {
            HandbookUrlPolicy.canonicalUrl(page.copy(encodedFragment = null))
        }.getOrNull()
    }

    private fun DataInputStream.readUtf8(maxBytes: Int): String {
        val byteCount = readInt()
        if (byteCount < 0 || byteCount > maxBytes) {
            throw IOException("Invalid handbook cache entry size")
        }
        val bytes = ByteArray(byteCount)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun DataOutputStream.writeUtf8(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                append(HEX_DIGITS[unsigned ushr 4])
                append(HEX_DIGITS[unsigned and 0x0F])
            }
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "handbook"
        const val CACHE_MAGIC = 0x48424B31
        const val CACHE_VERSION = 2
        const val CACHE_TTL_MS = 10 * 60 * 1_000L
        const val MAX_URL_BYTES = 64 * 1024
        const val MAX_CACHED_HTML_BYTES = 4 * 1024 * 1024
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
