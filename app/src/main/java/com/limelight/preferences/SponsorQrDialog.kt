package com.limelight.preferences

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.limelight.R
import com.limelight.utils.AppDialogStyler

internal class SponsorQrDialog(
    private val context: Context,
    private val bitmap: Bitmap,
    private val showWechatAction: Boolean
) {
    private var saveInProgress = false

    fun show() {
        val availableWidth = (context.resources.displayMetrics.widthPixels - dpToPx(80))
            .coerceAtLeast(dpToPx(160))
        val imageSize = minOf(
            availableWidth,
            dpToPx(420)
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(20))
        }
        val enlargedQr = createQrImage(imageSize)
        val celebrationLayer = createCelebrationLayer(imageSize)
        val hint = createHint()
        val openWechatButton = createWechatButton()

        enlargedQr.setOnLongClickListener {
            save(enlargedQr, celebrationLayer, hint, openWechatButton)
            true
        }

        content.addView(enlargedQr)
        content.addView(celebrationLayer)
        content.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(2)
        })
        content.addView(openWechatButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dpToPx(44)
        ).apply {
            topMargin = dpToPx(12)
        })

        val dialog = AlertDialog.Builder(context, R.style.AppDialogStyle)
            .setTitle(R.string.sponsor_qr_dialog_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener { AppDialogStyler.styleAlertDialog(dialog, context) }
        dialog.show()
    }

    private fun createQrImage(imageSize: Int) = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(imageSize, imageSize)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = context.getString(R.string.sponsor_qr_long_press_hint)
        setImageBitmap(bitmap)
        isClickable = true
        isLongClickable = true
    }

    private fun createHint() = TextView(context).apply {
        setText(R.string.sponsor_qr_long_press_hint)
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.app_dialog_text_primary))
    }

    private fun createWechatButton() = Button(context).apply {
        setText(R.string.sponsor_open_wechat)
        setBackgroundResource(R.drawable.sponsor_action_background)
        setTextColor(Color.WHITE)
        isAllCaps = false
        minHeight = dpToPx(40)
        minWidth = dpToPx(144)
        setPadding(dpToPx(18), 0, dpToPx(18), 0)
        isVisible = false
        setOnClickListener { openWechat() }
    }

    private fun save(
        qrView: ImageView,
        celebrationLayer: FrameLayout,
        hint: TextView,
        openWechatButton: Button
    ) {
        if (saveInProgress) return
        saveInProgress = true
        SponsorQrSaver(context, bitmap).save saveResult@{ saved ->
            saveInProgress = false
            if (!saved || !qrView.isAttachedToWindow) return@saveResult

            hint.setText(
                if (showWechatAction) R.string.sponsor_qr_saved_open_wechat_hint
                else R.string.sponsor_qr_saved
            )
            qrView.contentDescription = hint.text
            playSaveSuccessAnimation(qrView, celebrationLayer)
            if (showWechatAction) revealWechatButton(openWechatButton)
        }
    }

    private fun revealWechatButton(button: Button) {
        if (button.isVisible) return
        button.alpha = 0f
        button.translationY = dpToPx(8).toFloat()
        button.isVisible = true
        button.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun createCelebrationLayer(width: Int): FrameLayout {
        val layerHeight = dpToPx(42)
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, layerHeight)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.INVISIBLE
            HEART_POSITIONS.forEachIndexed { index, position ->
                addView(TextView(context).apply {
                    text = HEART_GLYPH
                    textSize = if (index == 1) 28f else 23f
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.theme_pink_primary))
                    alpha = 0f
                }, FrameLayout.LayoutParams(dpToPx(42), layerHeight).apply {
                    leftMargin = (width * position).toInt() - dpToPx(21)
                })
            }
        }
    }

    private fun playSaveSuccessAnimation(qrView: ImageView, celebrationLayer: FrameLayout) {
        qrView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        qrView.animate().cancel()
        qrView.scaleX = 1f
        qrView.scaleY = 1f
        qrView.animate()
            .scaleX(1.045f)
            .scaleY(1.045f)
            .setDuration(130L)
            .withEndAction {
                qrView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(280L)
                    .setInterpolator(OvershootInterpolator(2.2f))
                    .start()
            }
            .start()

        celebrationLayer.visibility = View.VISIBLE
        for (index in 0 until celebrationLayer.childCount) {
            val heart = celebrationLayer.getChildAt(index)
            heart.animate().cancel()
            heart.alpha = 0f
            heart.scaleX = 0.55f
            heart.scaleY = 0.55f
            heart.translationY = dpToPx(18).toFloat()
            heart.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(230L)
                .withEndAction {
                    heart.animate()
                        .alpha(0f)
                        .translationY(-dpToPx(16).toFloat())
                        .setStartDelay(130L)
                        .setDuration(320L)
                        .start()
                }
                .start()
        }
        celebrationLayer.postDelayed({ celebrationLayer.visibility = View.INVISIBLE }, 900L)
    }

    private fun openWechat() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(context, R.string.sponsor_wechat_not_installed, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(launchIntent) }
            .onFailure {
                Log.e(TAG, "Failed to open WeChat", it)
                Toast.makeText(context, R.string.sponsor_wechat_not_installed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        private const val TAG = "SponsorQrDialog"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val HEART_GLYPH = "♥"
        private val HEART_POSITIONS = floatArrayOf(0.25f, 0.5f, 0.75f)
    }
}
