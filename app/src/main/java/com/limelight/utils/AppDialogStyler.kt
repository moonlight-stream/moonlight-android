package com.limelight.utils

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.limelight.R

object AppDialogStyler {
    fun apply(dialog: Dialog, context: Context) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.app_dialog_bg_cute)
        applyChrome(dialog, context)
    }

    fun applySystemChoiceList(dialog: Dialog, context: Context) {
        apply(dialog, context)
        styleSystemChoiceList(findAlertListView(dialog), context)
    }

    fun applyCustomContent(dialog: Dialog, context: Context) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        applyChrome(dialog, context)
    }

    fun applyChrome(dialog: Dialog, context: Context) {
        tintTitle(dialog, context)
        tintButtons(dialog, context)
        clearTextShadows(dialog.window?.decorView)
    }

    fun styleAlertDialog(dialog: Dialog, context: Context) {
        apply(dialog, context)
    }

    fun tintTitle(dialog: Dialog, context: Context) {
        val titleColor = ContextCompat.getColor(context, R.color.app_dialog_title_color)
        listOf(
            context.resources.getIdentifier("alertTitle", "id", context.packageName),
            context.resources.getIdentifier("alertTitle", "id", "android")
        ).filter { it != 0 }
            .distinct()
            .forEach { titleId ->
                dialog.findViewById<TextView>(titleId)?.let { titleView ->
                    titleView.setTextColor(titleColor)
                    titleView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                }
            }
    }

    fun tintButtons(dialog: Dialog, context: Context) {
        val buttonTextColors = ContextCompat.getColorStateList(context, R.color.app_dialog_button_text)
        listOf(
            DialogInterface.BUTTON_POSITIVE,
            DialogInterface.BUTTON_NEGATIVE,
            DialogInterface.BUTTON_NEUTRAL
        ).forEach { buttonId ->
            buttonTextColors?.let { findButton(dialog, buttonId)?.setTextColor(it) }
        }
    }

    fun tintChoiceText(textView: TextView, context: Context) {
        textView.setTextColor(ContextCompat.getColor(context, R.color.app_dialog_text_primary))
        textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    fun bindChoiceRow(row: View, textView: TextView, context: Context, checked: Boolean) {
        row.isActivated = checked
        if (textView is CheckedTextView) {
            textView.isChecked = checked
        }
        tintChoiceText(textView, context)
    }

    fun clearTextShadows(view: View?) {
        when (view) {
            is TextView -> view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    clearTextShadows(view.getChildAt(index))
                }
            }
        }
    }

    fun applyAboutDialog(dialog: Dialog, context: Context) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_about_window_bg)
        val accentColor = ContextCompat.getColor(context, R.color.app_dialog_accent_color)
        listOf(
            DialogInterface.BUTTON_POSITIVE,
            DialogInterface.BUTTON_NEGATIVE,
            DialogInterface.BUTTON_NEUTRAL
        ).forEach { buttonId -> findButton(dialog, buttonId)?.setTextColor(accentColor) }
        compactAboutDialogActions(dialog, context)
    }

    private fun compactAboutDialogActions(dialog: Dialog, context: Context) {
        val buttonPanelId = context.resources.getIdentifier("buttonPanel", "id", "android")
        val buttonPanel = dialog.findViewById<ViewGroup>(buttonPanelId) ?: return

        buttonPanel.post {
            val actionHeight = dpToPx(context, 48)
            (buttonPanel.layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
                layoutParams.bottomMargin = 0
                buttonPanel.layoutParams = layoutParams
            }

            val buttons = listOf(
                DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL
            ).mapNotNull { buttonId -> findButton(dialog, buttonId) }

            (buttons.firstOrNull()?.parent as? ViewGroup)?.apply {
                setPaddingRelative(paddingStart, 0, paddingEnd, 0)
            }
            buttons.forEach { button ->
                button.apply {
                    minHeight = actionHeight
                    minimumHeight = actionHeight
                    setPaddingRelative(paddingStart, 0, paddingEnd, 0)
                }
            }

            buttonPanel.requestLayout()
        }
    }

    fun styleChoiceListContainer(listView: ListView?, context: Context) {
        listView ?: return
        listView.setBackgroundColor(Color.TRANSPARENT)
        listView.cacheColorHint = Color.TRANSPARENT
        listView.divider = ColorDrawable(Color.TRANSPARENT)
        listView.dividerHeight = dpToPx(context, 4)
        listView.selector = ColorDrawable(Color.TRANSPARENT)
        listView.setPadding(dpToPx(context, 10), dpToPx(context, 2), dpToPx(context, 10), dpToPx(context, 8))
        listView.clipToPadding = false
        listView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    fun styleSystemChoiceList(listView: ListView?, context: Context) {
        listView ?: return
        styleChoiceListContainer(listView, context)
        listView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                child?.let { styleListRow(it, context) }
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        listView.post {
            for (index in 0 until listView.childCount) {
                styleListRow(listView.getChildAt(index), context)
            }
            clearTextShadows(listView)
        }
    }

    private fun findButton(dialog: Dialog, buttonId: Int): TextView? {
        return when (dialog) {
            is android.app.AlertDialog -> dialog.getButton(buttonId)
            is androidx.appcompat.app.AlertDialog -> dialog.getButton(buttonId)
            else -> null
        }
    }

    private fun findAlertListView(dialog: Dialog): ListView? {
        return dialog.findViewById(android.R.id.list) ?: findListView(dialog.window?.decorView)
    }

    private fun findListView(view: View?): ListView? {
        return when (view) {
            is ListView -> view
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    findListView(view.getChildAt(index))?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun styleListRow(row: View, context: Context) {
        row.setBackgroundResource(R.drawable.app_dialog_list_item_bg)
        val textView = when (row) {
            is TextView -> row
            else -> row.findViewById<TextView>(android.R.id.text1)
        } ?: return

        tintChoiceText(textView, context)
        textView.minHeight = dpToPx(context, 46)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        textView.setPaddingRelative(
            dpToPx(context, 18),
            dpToPx(context, 9),
            dpToPx(context, 14),
            dpToPx(context, 9)
        )
    }

    private fun dpToPx(context: Context, value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}
