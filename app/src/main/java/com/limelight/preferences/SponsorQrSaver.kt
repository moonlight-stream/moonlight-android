package com.limelight.preferences

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.limelight.R
import java.io.File
import java.io.FileOutputStream

internal class SponsorQrSaver(
    private val context: Context,
    private val bitmap: Bitmap
) {
    fun save(onResult: (Boolean) -> Unit) {
        if (requiresLegacyStoragePermission()) {
            requestLegacyStoragePermission()
            onResult(false)
            return
        }

        Thread {
            val saved = runCatching { writeQrToGallery() }
                .onFailure { Log.e(TAG, "Failed to save sponsor QR code", it) }
                .isSuccess
            postToMain {
                Toast.makeText(
                    context,
                    if (saved) R.string.sponsor_qr_saved else R.string.sponsor_qr_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
                onResult(saved)
            }
        }.start()
    }

    private fun requiresLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    private fun requestLegacyStoragePermission() {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, R.string.sponsor_qr_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
        Toast.makeText(context, R.string.sponsor_qr_permission_retry, Toast.LENGTH_LONG).show()
    }

    private fun writeQrToGallery() {
        val fileName = "moonlight-v-plus-sponsor-qr-${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeQrToMediaStore(fileName)
        } else {
            writeQrToLegacyGallery(fileName)
        }
    }

    private fun writeQrToMediaStore(fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Moonlight V+")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } ?: error("Unable to open MediaStore output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun writeQrToLegacyGallery(fileName: String) {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val directory = File(pictures, "Moonlight V+").apply { check(exists() || mkdirs()) }
        val file = File(directory, fileName)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/png"),
            null
        )
    }

    private fun postToMain(action: () -> Unit) {
        android.os.Handler(context.mainLooper).post(action)
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private companion object {
        private const val TAG = "SponsorQrSaver"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 8401
    }
}
