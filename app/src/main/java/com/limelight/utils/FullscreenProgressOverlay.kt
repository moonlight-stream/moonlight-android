package com.limelight.utils

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import java.util.Random

class FullscreenProgressOverlay(
    private val activity: Activity,
    private val app: NvApp?
) {
    private val overlayView: View
    private val statusText: TextView
    private val progressText: TextView
    private val randomTip: TextView
    private val appPosterBackgroundBlur: ImageView
    private val appPosterBackgroundClear: ImageView
    private val progressBar: ProgressBar
    private val rootView: ViewGroup
    private val tips: Array<String>
    private val random = Random()
    private val backgroundMode = AppBackgroundMode.read(activity)
    private var isShowing = false
    private var posterRequestSerial = 0
    var computer: ComputerDetails? = null

    init {
        tips = arrayOf(
            activity.getString(R.string.tip_esc_exit),
            activity.getString(R.string.tip_double_tap_mouse),
            activity.getString(R.string.tip_long_press_controller),
            activity.getString(R.string.tip_volume_keys),
            activity.getString(R.string.tip_wallpaper_change),
            activity.getString(R.string.tip_5ghz_wifi),
            activity.getString(R.string.tip_close_apps),
            activity.getString(R.string.tip_home_saves),
            activity.getString(R.string.tip_hdr_colors),
            activity.getString(R.string.tip_touch_modes),
            activity.getString(R.string.tip_custom_keys),
            activity.getString(R.string.tip_performance_overlay),
            activity.getString(R.string.tip_audio_config),
            activity.getString(R.string.tip_external_display),
            activity.getString(R.string.tip_virtual_display),
            activity.getString(R.string.tip_dynamic_bitrate),
            activity.getString(R.string.tip_cards_show)
        )

        rootView = activity.findViewById(android.R.id.content)

        val inflater = LayoutInflater.from(activity)
        overlayView = inflater.inflate(R.layout.fullscreen_progress_overlay, rootView, false)

        statusText = overlayView.findViewById(R.id.statusText)
        progressText = overlayView.findViewById(R.id.progressText)
        randomTip = overlayView.findViewById(R.id.randomTip)
        appPosterBackgroundBlur = overlayView.findViewById(R.id.appPosterBackgroundBlur)
        appPosterBackgroundClear = overlayView.findViewById(R.id.appPosterBackgroundClear)
        progressBar = overlayView.findViewById(R.id.progressBar)

        overlayView.visibility = View.GONE
    }

    fun show(title: String, message: String) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (!isShowing) {
                statusText.text = title
                progressText.text = message

                val tip = tips[random.nextInt(tips.size)]
                randomTip.text = tip

                if (overlayView.parent == null) {
                    rootView.addView(overlayView)
                }

                overlayView.visibility = View.VISIBLE
                isShowing = true

                applySoftColorFallback()
                loadAppImage()
            }
        }
    }

    fun setMessage(message: String) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (isShowing) {
                progressText.text = message
            }
        }
    }

    fun setStatus(status: String) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (isShowing) {
                statusText.text = status
            }
        }
    }

    fun setAppPoster(poster: Bitmap?) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (poster != null) {
                applyPoster(poster)
            } else {
                applyMissingPoster()
            }
        }
    }

    fun setAppPoster(poster: Drawable?) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            when {
                poster == null -> applyMissingPoster()
                else -> applyDrawablePoster(poster)
            }
        }
    }

    fun setProgress(progress: Int) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (isShowing) {
                progressBar.isIndeterminate = false
                progressBar.progress = progress
            }
        }
    }

    fun setIndeterminate(indeterminate: Boolean) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (isShowing) {
                progressBar.isIndeterminate = indeterminate
            }
        }
    }

    fun dismiss() {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            if (isShowing) {
                isShowing = false
                posterRequestSerial++
                overlayView.animate().cancel()
                overlayView.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        overlayView.visibility = View.GONE
                        overlayView.alpha = 1f
                        appPosterBackgroundBlur.setImageDrawable(null)
                        appPosterBackgroundClear.setImageDrawable(null)
                        if (overlayView.parent != null) {
                            rootView.removeView(overlayView)
                        }
                    }
                    .start()
            }
        }
    }

    fun isShowing(): Boolean = isShowing

    private fun loadAppImage() {
        val curApp = app ?: return
        val curComputer = computer ?: return
        val cached = AppIconCache.instance.getFullIcon(curComputer, curApp)
        if (cached != null) {
            applyPoster(cached)
            return
        }

        // 内存 cache miss（冷启动 / 快捷方式 / Trampoline 入口）：fallback 到磁盘缓存
        val uuid = curComputer.uuid ?: return
        val appId = curApp.appId
        val appCtx = activity.applicationContext
        val dm = appCtx.resources.displayMetrics
        val maxDim = kotlin.math.max(dm.widthPixels, dm.heightPixels).coerceAtMost(1920)
        Thread({
            val bitmap = try {
                com.limelight.grid.assets.DiskAssetLoader(appCtx)
                    .loadFullBitmapFromCache(uuid, appId, maxDim)
            } catch (t: Throwable) {
                null
            } ?: return@Thread
            AppIconCache.instance.putFullIcon(curComputer, curApp, bitmap)
            activity.runOnUiThread {
                if (isShowing) applyPoster(bitmap)
            }
        }, "OverlayPosterLoader").start()
    }

    private fun applyPoster(bitmap: Bitmap) {
        when (backgroundMode) {
            AppBackgroundMode.Artwork -> applyArtwork(bitmap)
            AppBackgroundMode.Acrylic -> applyAcrylic(bitmap)
            AppBackgroundMode.SoftColor -> applySoftColor(bitmap)
        }
    }

    /** 应用封面：保留连接页原有的模糊背景 + 完整封面双层显示。 */
    private fun applyArtwork(bitmap: Bitmap) {
        ++posterRequestSerial
        clearAcrylicMode()
        appPosterBackgroundBlur.visibility = View.VISIBLE
        appPosterBackgroundClear.visibility = View.VISIBLE
        appPosterBackgroundBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        appPosterBackgroundClear.scaleType = ImageView.ScaleType.FIT_CENTER
        appPosterBackgroundBlur.tag = bitmap
        appPosterBackgroundClear.tag = bitmap
        clearRenderEffect(appPosterBackgroundClear)
        BackgroundImageManager.setBlurredBitmap(
            appPosterBackgroundBlur,
            bitmap,
            BackgroundImageManager.OVERLAY_IMAGE_ALPHA
        )
        appPosterBackgroundClear.setImageBitmap(bitmap)
        appPosterBackgroundClear.imageAlpha = BackgroundImageManager.OVERLAY_IMAGE_ALPHA
    }

    /** 亚克力：全屏模糊底图 + 绘制阶段合成的中央半透明完整封面。 */
    private fun applyAcrylic(bitmap: Bitmap) {
        ++posterRequestSerial
        appPosterBackgroundBlur.visibility = View.VISIBLE
        appPosterBackgroundClear.visibility = View.VISIBLE
        appPosterBackgroundBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        appPosterBackgroundClear.scaleType = ImageView.ScaleType.FIT_CENTER
        appPosterBackgroundBlur.tag = bitmap
        appPosterBackgroundClear.tag = bitmap
        BackgroundImageManager.setBlurredBitmap(
            appPosterBackgroundBlur,
            bitmap,
            BackgroundImageManager.OVERLAY_IMAGE_ALPHA
        )
        setAcrylicBitmap(bitmap)
    }

    private fun applySoftColor(bitmap: Bitmap) {
        val requestId = ++posterRequestSerial
        val fallbackColor = app?.let { SoftBackgroundColorExtractor.fallbackFor(it) }
            ?: 0xFF4D464A.toInt()
        applyColor(fallbackColor)

        Thread({
            val color = SoftBackgroundColorExtractor.fromBitmap(bitmap, fallbackColor)
            activity.runOnUiThread {
                if (isShowing && requestId == posterRequestSerial) {
                    applyColor(color)
                }
            }
        }, "OverlayColorExtractor").start()
    }

    private fun applySoftColorFallback() {
        if (backgroundMode != AppBackgroundMode.SoftColor) return
        val fallbackColor = app?.let { SoftBackgroundColorExtractor.fallbackFor(it) }
            ?: 0xFF4D464A.toInt()
        applyColor(fallbackColor)
    }

    private fun applyColor(color: Int) {
        appPosterBackgroundBlur.tag = null
        appPosterBackgroundClear.tag = null
        appPosterBackgroundClear.setImageDrawable(null)
        clearAcrylicMode()
        appPosterBackgroundBlur.visibility = View.VISIBLE
        appPosterBackgroundClear.visibility = View.GONE
        clearRenderEffect(appPosterBackgroundBlur)
        appPosterBackgroundBlur.setImageDrawable(android.graphics.drawable.ColorDrawable(color))
        appPosterBackgroundBlur.imageAlpha = 255
    }

    private fun applyDrawablePoster(drawable: Drawable) {
        if (backgroundMode == AppBackgroundMode.SoftColor) {
            applySoftColorFallback()
            return
        }

        ++posterRequestSerial
        appPosterBackgroundBlur.visibility = View.VISIBLE
        appPosterBackgroundClear.visibility = View.VISIBLE
        appPosterBackgroundBlur.scaleType = ImageView.ScaleType.CENTER_CROP
        appPosterBackgroundClear.scaleType = ImageView.ScaleType.FIT_CENTER
        appPosterBackgroundBlur.tag = drawable
        appPosterBackgroundClear.tag = drawable
        clearRenderEffect(appPosterBackgroundClear)
        BackgroundImageManager.setBlurredDrawable(
            appPosterBackgroundBlur,
            drawable,
            BackgroundImageManager.OVERLAY_IMAGE_ALPHA
        )

        if (backgroundMode == AppBackgroundMode.Acrylic) {
            setAcrylicDrawable(drawable)
        } else {
            clearAcrylicMode()
            appPosterBackgroundClear.setImageDrawable(drawable)
            appPosterBackgroundClear.imageAlpha = BackgroundImageManager.OVERLAY_IMAGE_ALPHA
        }
    }

    private fun applyMissingPoster() {
        ++posterRequestSerial
        appPosterBackgroundBlur.tag = null
        appPosterBackgroundClear.tag = null
        clearAcrylicMode()
        appPosterBackgroundBlur.setImageDrawable(null)
        appPosterBackgroundClear.setImageDrawable(null)

        if (backgroundMode == AppBackgroundMode.SoftColor) {
            applySoftColorFallback()
            return
        }

        appPosterBackgroundBlur.visibility = View.VISIBLE
        appPosterBackgroundClear.visibility = View.GONE
        appPosterBackgroundBlur.setImageResource(R.drawable.no_app_image)
    }

    private fun setAcrylicBitmap(bitmap: Bitmap) {
        val acrylicImageView = appPosterBackgroundClear as? AcrylicImageView
        if (acrylicImageView != null) {
            acrylicImageView.setAcrylicBitmap(
                bitmap,
                BackgroundImageManager.OVERLAY_IMAGE_ALPHA
            )
        } else {
            appPosterBackgroundClear.setImageBitmap(bitmap)
            appPosterBackgroundClear.imageAlpha = BackgroundImageManager.OVERLAY_IMAGE_ALPHA
        }
    }

    private fun setAcrylicDrawable(drawable: Drawable) {
        val acrylicImageView = appPosterBackgroundClear as? AcrylicImageView
        if (acrylicImageView != null) {
            acrylicImageView.setAcrylicDrawable(
                drawable,
                BackgroundImageManager.OVERLAY_IMAGE_ALPHA
            )
        } else {
            appPosterBackgroundClear.setImageDrawable(drawable)
            appPosterBackgroundClear.imageAlpha = BackgroundImageManager.OVERLAY_IMAGE_ALPHA
        }
    }

    private fun clearAcrylicMode() {
        (appPosterBackgroundClear as? AcrylicImageView)?.clearAcrylicMode()
    }

    private fun clearRenderEffect(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageView.setRenderEffect(null)
        }
    }
}
