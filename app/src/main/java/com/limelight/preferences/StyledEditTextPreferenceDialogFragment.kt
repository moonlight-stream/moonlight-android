package com.limelight.preferences

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.limelight.R
import com.limelight.utils.AppDialogStyler

class StyledEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppDialogStyle)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        styleEditText(view.findViewById(android.R.id.edit))
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.drawable.app_dialog_bg_cute)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val alert = dialog as? AlertDialog ?: return
        AppDialogStyler.tintTitle(alert, requireContext())
        AppDialogStyler.installDismissKeys(alert)
        tintDialogButtons(alert)
    }

    private fun tintDialogButtons(dialog: AlertDialog) {
        val accentColor = ContextCompat.getColor(requireContext(), R.color.app_dialog_accent_color)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { buttonId ->
                dialog.getButton(buttonId)?.setTextColor(accentColor)
            }
    }

    private fun styleEditText(editText: EditText?) {
        editText ?: return
        val context = editText.context
        editText.setTextColor(ContextCompat.getColor(context, R.color.app_dialog_text_primary))
        editText.setHintTextColor(ContextCompat.getColor(context, R.color.app_dialog_text_secondary))
        editText.setBackgroundResource(R.drawable.custom_resolution_input_bg)
        editText.setPadding(dpToPx(12))
        editText.minHeight = dpToPx(48)
        editText.textSize = 15f
    }

    private fun dpToPx(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    companion object {
        fun newInstance(key: String): StyledEditTextPreferenceDialogFragment {
            val fragment = StyledEditTextPreferenceDialogFragment()
            val args = Bundle(1)
            args.putString(ARG_KEY, key)
            fragment.arguments = args
            return fragment
        }
    }
}
