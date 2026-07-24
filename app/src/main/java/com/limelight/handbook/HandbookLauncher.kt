package com.limelight.handbook

import android.app.Activity
import android.content.Context
import android.content.Intent

object HandbookLauncher {
    private const val EXTRA_PAGE_URL = "com.limelight.handbook.PAGE_URL"

    fun openIndex(context: Context) {
        open(context, HandbookUrlPolicy.index)
    }

    fun openUrl(context: Context, url: String): Boolean {
        val page = HandbookUrlPolicy.parse(url) ?: return false
        open(context, page)
        return true
    }

    internal fun pageFromIntent(intent: Intent): HandbookPageRef {
        return HandbookUrlPolicy.parse(intent.getStringExtra(EXTRA_PAGE_URL))
            ?: HandbookUrlPolicy.index
    }

    private fun open(context: Context, page: HandbookPageRef) {
        val intent = Intent(context, HandbookActivity::class.java)
            .putExtra(EXTRA_PAGE_URL, HandbookUrlPolicy.canonicalUrl(page))
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
