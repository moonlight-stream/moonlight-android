package com.limelight.preferences

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceDialogFragmentCompat
import com.limelight.R
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader.OSC_PREFERENCE
import com.limelight.utils.AppDialogStyler

class ConfirmDeleteOscDialogFragment : PreferenceDialogFragmentCompat() {

    override fun onStart() {
        super.onStart()
        dialog?.let(AppDialogStyler::installDismissKeys)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val ctx = requireContext()
            ctx.getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE).edit().clear().apply()
            Toast.makeText(ctx, R.string.toast_reset_osc_success, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance(key: String): ConfirmDeleteOscDialogFragment {
            val fragment = ConfirmDeleteOscDialogFragment()
            val args = Bundle(1)
            args.putString(ARG_KEY, key)
            fragment.arguments = args
            return fragment
        }
    }
}
