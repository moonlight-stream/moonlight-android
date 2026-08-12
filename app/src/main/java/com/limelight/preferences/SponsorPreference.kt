package com.limelight.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.limelight.R
import com.limelight.utils.HelpLauncher

class SponsorPreference : Preference {
    private var cachedGithubQrContent: String? = null
    private var cachedGithubQrBitmap: Bitmap? = null
    private val wechatQrBitmap: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.sponsor_wechat_qr))
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) { initialize() }

    constructor(context: Context, attrs: AttributeSet) :
            super(context, attrs) { initialize() }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) { initialize() }

    private fun initialize() {
        layoutResource = R.layout.preference_sponsor_panel
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val isChinese = isChineseLocale()
        val titleView = holder.findViewById(R.id.sponsorTitle) as TextView
        val summaryView = holder.findViewById(R.id.sponsorSummary) as TextView
        val actionView = holder.findViewById(R.id.sponsorAction) as TextView
        val qrView = holder.findViewById(R.id.sponsorQr) as ImageView

        titleView.setText(if (isChinese) R.string.sponsor_wechat_title else R.string.sponsor_github_title)
        summaryView.setText(if (isChinese) R.string.sponsor_wechat_summary else R.string.sponsor_github_summary)

        if (isChinese) {
            actionView.setText(R.string.sponsor_wechat_action)
            qrView.setImageBitmap(wechatQrBitmap)
            bindQrActions(qrView, wechatQrBitmap, showWechatAction = true)
            actionView.visibility = View.GONE
        } else {
            val githubUrl = context.getString(R.string.sponsor_github_url)
            val qrBitmap = getGithubQrBitmap(githubUrl)
            actionView.setText(R.string.sponsor_github_action)
            qrView.setImageBitmap(qrBitmap)
            bindQrActions(qrView, qrBitmap, showWechatAction = false)
            actionView.visibility = View.VISIBLE
            actionView.setOnClickListener { HelpLauncher.launchUrl(context, githubUrl) }
        }
    }

    private fun bindQrActions(qrView: ImageView, bitmap: Bitmap, showWechatAction: Boolean) {
        qrView.setOnClickListener {
            SponsorQrDialog(context, bitmap, showWechatAction).show()
        }
    }

    private fun getGithubQrBitmap(content: String): Bitmap {
        val cachedBitmap = cachedGithubQrBitmap
        if (cachedGithubQrContent == content && cachedBitmap != null) {
            return cachedBitmap
        }

        return createQrBitmap(content, QR_SIZE_PX).also {
            cachedGithubQrContent = content
            cachedGithubQrBitmap = it
        }
    }

    private fun isChineseLocale(): Boolean {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        for (i in 0 until locales.size()) {
            if (locales[i]?.language == "zh") {
                return true
            }
        }
        return false
    }

    private fun createQrBitmap(content: String, sizePx: Int): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }

    private companion object {
        private const val QR_SIZE_PX = 512
    }
}
