package com.limelight.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.graphics.drawable.ColorDrawable
import java.util.concurrent.Executors

import com.limelight.R

/**
 * 背景图片管理器，用于处理AppView背景图片的平滑切换。
 * - “应用封面”：横屏使用模糊层 + 清晰层，竖屏使用单层模糊封面。
 * - “亚克力”：恢复旧版的全屏模糊底图 + 中央半透明完整封面。
 * - “柔和纯色”：使用从应用封面提取的纯色。
 */
class BackgroundImageManager(
    private val context: Context,
    private val blurImageView: ImageView,
    private val clearImageView: ImageView,
    private val artworkBlurOnly: Boolean = false
) {
    private enum class BitmapStyle {
        Artwork,
        Acrylic
    }

    var currentBackground: Bitmap? = null
        private set
    private var currentSolidColor: Int? = null
    private var currentBitmapStyle: BitmapStyle? = null
    val hasBackground: Boolean
        get() = currentBackground != null || currentSolidColor != null

    /** 应用封面模式：保留当前版本的渲染方式。 */
    fun setBackgroundSmoothly(newBackground: Bitmap?) {
        setBitmapBackgroundSmoothly(newBackground, BitmapStyle.Artwork)
    }

    /** 亚克力模式：全屏模糊底图 + 中央半透明完整封面。 */
    fun setAcrylicBackgroundSmoothly(newBackground: Bitmap?) {
        setBitmapBackgroundSmoothly(newBackground, BitmapStyle.Acrylic)
    }

    private fun setBitmapBackgroundSmoothly(newBackground: Bitmap?, style: BitmapStyle) {
        if (newBackground == null || newBackground.isRecycled) {
            return
        }

        if (currentBackground == newBackground && currentBitmapStyle == style) {
            return
        }

        if (currentBackground == null) {
            currentBackground = newBackground
            currentSolidColor = null
            currentBitmapStyle = style
            applyBitmap(newBackground, style)
            blurImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
            if (clearImageView.visibility == View.VISIBLE) {
                clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
            }
            return
        }

        val fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.background_fadeout)
        fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                currentBackground = newBackground
                currentSolidColor = null
                currentBitmapStyle = style
                applyBitmap(newBackground, style)
                blurImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
                if (clearImageView.visibility == View.VISIBLE) {
                    clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })

        blurImageView.startAnimation(fadeOutAnimation)
        if (clearImageView.visibility == View.VISIBLE) {
            clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadeout))
        }
    }

    fun setBackgroundColorSmoothly(color: Int) {
        if (currentSolidColor == color) {
            return
        }

        if (!hasBackground) {
            currentBackground = null
            currentSolidColor = color
            currentBitmapStyle = null
            applyColor(color)
            blurImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
            if (clearImageView.visibility == View.VISIBLE) {
                clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
            }
            return
        }

        val fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.background_fadeout)
        fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                currentBackground = null
                currentSolidColor = color
                currentBitmapStyle = null
                applyColor(color)
                blurImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
                if (clearImageView.visibility == View.VISIBLE) {
                    clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadein))
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })

        blurImageView.startAnimation(fadeOutAnimation)
        if (clearImageView.visibility == View.VISIBLE) {
            clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadeout))
        }
    }

    /** 同一张 Bitmap 驱动不同背景模式；亚克力模式复用旧版的合成效果。 */
    private fun applyBitmap(bitmap: Bitmap, style: BitmapStyle) {
        when (style) {
            BitmapStyle.Artwork -> {
                clearAcrylicMode()
                if (artworkBlurOnly) {
                    clearImageView.tag = null
                    clearImageView.setImageDrawable(null)
                    clearImageView.visibility = View.GONE
                    blurImageView.visibility = View.VISIBLE
                    setBlurredBitmap(blurImageView, bitmap, BLUR_IMAGE_ALPHA)
                } else {
                    blurImageView.visibility = View.VISIBLE
                    clearImageView.visibility = View.VISIBLE
                    setBlurredBitmap(
                        blurImageView,
                        bitmap,
                        BLUR_IMAGE_ALPHA_PAIRED,
                        RENDER_EFFECT_RADIUS_PAIRED,
                        BLUR_RADIUS_PAIRED
                    )
                    clearImageView.tag = null
                    clearImageView.setImageBitmap(bitmap)
                    clearImageView.imageAlpha = CLEAR_IMAGE_ALPHA
                }
            }
            BitmapStyle.Acrylic -> {
                blurImageView.visibility = View.VISIBLE
                clearImageView.visibility = View.VISIBLE
                setBlurredBitmap(blurImageView, bitmap, BLUR_IMAGE_ALPHA)
                clearImageView.tag = bitmap
                setAcrylicBitmap(bitmap, CLEAR_IMAGE_ALPHA)
            }
        }
    }

    private fun clearAcrylicMode() {
        (clearImageView as? AcrylicImageView)?.clearAcrylicMode()
    }

    private fun setAcrylicBitmap(bitmap: Bitmap, alpha: Int) {
        val acrylicImageView = clearImageView as? AcrylicImageView
        if (acrylicImageView != null) {
            acrylicImageView.setAcrylicBitmap(bitmap, alpha)
        } else {
            clearImageView.setImageBitmap(bitmap)
            clearImageView.imageAlpha = alpha
        }
    }

    private fun applyColor(color: Int) {
        blurImageView.tag = null
        clearImageView.tag = null
        clearAcrylicMode()
        clearImageView.setImageDrawable(null)
        blurImageView.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurImageView.setRenderEffect(null)
        }
        blurImageView.setImageDrawable(ColorDrawable(color))
        blurImageView.imageAlpha = 255

        if (artworkBlurOnly) {
            clearImageView.visibility = View.GONE
            return
        }

        clearImageView.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            clearImageView.setRenderEffect(null)
        }
        clearImageView.setImageDrawable(ColorDrawable(color))
        clearImageView.imageAlpha = 255
    }

    /**
     * 清除背景图片
     */
    fun clearBackground() {
        if (hasBackground) {
            val fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.background_fadeout)
            fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}

                override fun onAnimationEnd(animation: Animation) {
                    blurImageView.tag = null
                    blurImageView.setImageDrawable(null)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        blurImageView.setRenderEffect(null)
                    }
                    clearImageView.tag = null
                    clearAcrylicMode()
                    clearImageView.setImageDrawable(null)
                    currentBackground = null
                    currentSolidColor = null
                    currentBitmapStyle = null
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })

            blurImageView.startAnimation(fadeOutAnimation)
            if (clearImageView.visibility == View.VISIBLE) {
                clearImageView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.background_fadeout))
            }
        }
    }

    companion object {
        private const val CLEAR_IMAGE_ALPHA = 160 // ~63%
        private const val BLUR_IMAGE_ALPHA = 160  // ~63%
        // 横屏双层下的 blur 背景：更弱且更朦胧，让中间清晰大图更突出
        private const val BLUR_IMAGE_ALPHA_PAIRED = 100      // ~39%
        private const val RENDER_EFFECT_RADIUS_PAIRED = 60f  // 原 25f
        private const val BLUR_RADIUS_PAIRED = 22            // 原 10
        const val OVERLAY_IMAGE_ALPHA = 180       // ~70%，连接页历史最终参数
        private const val BLUR_RADIUS = 10
        private const val RENDER_EFFECT_RADIUS = 25f

        private val blurExecutor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * 设置模糊图片到ImageView
         * Android 12+: GPU加速的RenderEffect，零额外内存分配
         * 低版本: 后台线程StackBlur
         */
        fun setBlurredBitmap(
            imageView: ImageView,
            bitmap: Bitmap,
            alpha: Int,
            renderEffectRadius: Float = RENDER_EFFECT_RADIUS,
            stackBlurRadius: Int = BLUR_RADIUS
        ) {
            if (bitmap.isRecycled) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                imageView.setImageBitmap(bitmap)
                imageView.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        renderEffectRadius, renderEffectRadius,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                )
                imageView.imageAlpha = alpha
            } else {
                imageView.tag = bitmap
                blurExecutor.execute {
                    if (bitmap.isRecycled) return@execute
                    val blurred = stackBlur(bitmap, stackBlurRadius)
                    mainHandler.post {
                        if (imageView.tag === bitmap) {
                            imageView.setImageBitmap(blurred)
                            imageView.imageAlpha = alpha
                        }
                    }
                }
            }
        }

        /**
         * 设置模糊Drawable到ImageView
         */
        fun setBlurredDrawable(imageView: ImageView, drawable: android.graphics.drawable.Drawable, alpha: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                imageView.setImageDrawable(drawable)
                imageView.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        RENDER_EFFECT_RADIUS, RENDER_EFFECT_RADIUS,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                )
                imageView.imageAlpha = alpha
            } else if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bmp = drawable.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    setBlurredBitmap(imageView, bmp, alpha)
                    return
                }
                imageView.setImageDrawable(drawable)
                imageView.imageAlpha = alpha
            } else {
                imageView.setImageDrawable(drawable)
                imageView.imageAlpha = alpha
            }
        }

        /**
         * StackBlur 算法 - 对缩小后的图片进行模糊处理以提升性能
         */
        fun stackBlur(original: Bitmap, radius: Int): Bitmap {
            if (original.isRecycled) return original
            try {
                val src = if (Build.VERSION.SDK_INT >= 26 &&
                    original.config == Bitmap.Config.HARDWARE) {
                    original.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    original
                }
                val scaleFactor = 3
                val smallWidth = (src.width / scaleFactor).coerceAtLeast(1)
                val smallHeight = (src.height / scaleFactor).coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(src, smallWidth, smallHeight, true)

            val bitmap = small.copy(Bitmap.Config.ARGB_8888, true)
            // 回收中间Bitmap
            if (small !== src && small !== original) small.recycle()
            if (src !== original) src.recycle()

            val w = bitmap.width
            val h = bitmap.height
            val pix = IntArray(w * h)
            bitmap.getPixels(pix, 0, w, 0, 0, w, h)

            val wm = w - 1
            val hm = h - 1
            val wh = w * h
            val div = radius + radius + 1

            val r = IntArray(wh)
            val g = IntArray(wh)
            val b = IntArray(wh)
            var rsum: Int; var gsum: Int; var bsum: Int
            var rinsum: Int; var ginsum: Int; var binsum: Int
            var routsum: Int; var goutsum: Int; var boutsum: Int
            var p: Int; var yp: Int; var yi: Int

            val vmin = IntArray(w.coerceAtLeast(h))
            val divsum = (div + 1) shr 1
            val dv = IntArray(256 * divsum * divsum)
            for (i in dv.indices) {
                dv[i] = i / (divsum * divsum)
            }

            var stackpointer: Int
            var stackstart: Int
            var sir: IntArray
            var rbs: Int

            val stack = Array(div) { IntArray(3) }

            var r1 = radius + 1

            yi = 0
            for (y in 0 until h) {
                rsum = 0; gsum = 0; bsum = 0
                rinsum = 0; ginsum = 0; binsum = 0
                routsum = 0; goutsum = 0; boutsum = 0
                for (i in -radius..radius) {
                    p = pix[yi + i.coerceIn(0, wm)]
                    sir = stack[i + radius]
                    sir[0] = (p and 0xff0000) shr 16
                    sir[1] = (p and 0x00ff00) shr 8
                    sir[2] = (p and 0x0000ff)
                    rbs = r1 - kotlin.math.abs(i)
                    rsum += sir[0] * rbs
                    gsum += sir[1] * rbs
                    bsum += sir[2] * rbs
                    if (i > 0) {
                        rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                    } else {
                        routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                    }
                }
                stackpointer = radius
                for (x in 0 until w) {
                    r[yi] = dv[rsum]
                    g[yi] = dv[gsum]
                    b[yi] = dv[bsum]

                    rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                    stackstart = stackpointer - radius + div
                    sir = stack[stackstart % div]
                    routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                    if (y == 0) vmin[x] = (x + radius + 1).coerceAtMost(wm)
                    p = pix[vmin[x] + y * w]  // Fixed: use y*w offset
                    sir[0] = (p and 0xff0000) shr 16
                    sir[1] = (p and 0x00ff00) shr 8
                    sir[2] = (p and 0x0000ff)
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                    rsum += rinsum; gsum += ginsum; bsum += binsum

                    stackpointer = (stackpointer + 1) % div
                    sir = stack[stackpointer % div]
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                    rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                    yi++
                }
            }

            for (x in 0 until w) {
                rsum = 0; gsum = 0; bsum = 0
                rinsum = 0; ginsum = 0; binsum = 0
                routsum = 0; goutsum = 0; boutsum = 0
                yp = -radius * w
                for (i in -radius..radius) {
                    yi = 0.coerceAtLeast(yp) + x
                    sir = stack[i + radius]
                    sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                    rbs = r1 - kotlin.math.abs(i)
                    rsum += r[yi] * rbs
                    gsum += g[yi] * rbs
                    bsum += b[yi] * rbs
                    if (i > 0) {
                        rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                    } else {
                        routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                    }
                    if (i < hm) yp += w
                }
                yi = x
                stackpointer = radius
                for (y in 0 until h) {
                    pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                    rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                    stackstart = stackpointer - radius + div
                    sir = stack[stackstart % div]
                    routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                    if (x == 0) vmin[y] = (y + r1).coerceAtMost(hm) * w
                    p = x + vmin[y]
                    sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                    rsum += rinsum; gsum += ginsum; bsum += binsum

                    stackpointer = (stackpointer + 1) % div
                    sir = stack[stackpointer]
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                    rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                    yi += w
                }
            }

            bitmap.setPixels(pix, 0, w, 0, 0, w, h)
            return bitmap
            } catch (e: Throwable) {
                return original
            }
        }
    }
}
