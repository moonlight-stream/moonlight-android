package com.limelight.preferences

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceDialogFragmentCompat

import com.limelight.R
import com.limelight.utils.AppDialogStyler
import kotlin.math.roundToInt

class SeekBarPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    private var seekBar: SeekBar? = null
    private lateinit var valueText: TextView

    private val pref: SeekBarPreference
        get() = preference as SeekBarPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppDialogStyle)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(R.drawable.app_dialog_bg_cute)
        tintDialogButtons()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindDialogView(layout: View) {
        super.onBindDialogView(layout)

        val pref = pref
        // 确保从持久化存储加载最新值
        pref.refreshCurrentValue()

        // Message text
        val messageView = layout.findViewById<TextView>(R.id.pref_seekbar_message)
        if (pref.dialogMessageText != null) {
            messageView.text = pref.dialogMessageText
            messageView.visibility = View.VISIBLE
        }

        // Value display
        valueText = layout.findViewById(R.id.pref_seekbar_value)

        // +/- buttons (logarithmic mode only)
        val btnMinus = layout.findViewById<ImageView>(R.id.pref_seekbar_btn_minus)
        val btnPlus = layout.findViewById<ImageView>(R.id.pref_seekbar_btn_plus)
        if (pref.isLogarithmic) {
            btnMinus.setImageResource(R.drawable.ic_pref_minus)
            btnPlus.setImageResource(R.drawable.ic_pref_plus)
            btnMinus.visibility = View.VISIBLE
            btnPlus.visibility = View.VISIBLE
            setupLongPressView(btnMinus, -1)
            setupLongPressView(btnPlus, 1)
        }

        // SeekBar
        seekBar = layout.findViewById(R.id.pref_seekbar)
        seekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (value < pref.minValue) {
                    seekBar.progress = pref.minValue
                    return
                }

                if (!pref.isLogarithmic) {
                    val roundedValue = maxOf(pref.minValue, (value.toFloat() / pref.stepSize).roundToInt() * pref.stepSize)
                    if (roundedValue != value) {
                        seekBar.progress = roundedValue
                        return
                    }
                }

                updateValueText(if (pref.isLogarithmic) pref.linearToLog(value) else value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Tick marks
        if (pref.showTickMarks && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val tickDrawable: Drawable? = requireContext().getDrawable(R.drawable.pref_seekbar_tick)
            seekBar!!.tickMark = tickDrawable
        }

        // Range labels
        val minLabel = layout.findViewById<TextView>(R.id.pref_seekbar_min_label)
        val maxLabel = layout.findViewById<TextView>(R.id.pref_seekbar_max_label)
        minLabel.text = pref.formatDisplayValue(pref.minValue)
        maxLabel.text = pref.formatDisplayValue(pref.maxValue)

        // Initialize seekbar
        seekBar!!.max = pref.maxValue
        if (pref.keyStepSize != 0) {
            seekBar!!.keyProgressIncrement = pref.keyStepSize
        }
        seekBar!!.progress = if (pref.isLogarithmic && pref.currentValue > 0) {
            pref.logToLinear(pref.currentValue)
        } else {
            pref.currentValue
        }

        seekBar!!.post { seekBar!!.requestFocus() }
    }

    private fun updateValueText(displayValue: Int) {
        val pref = pref
        var text = pref.formatDisplayValue(displayValue)
        if (pref.suffix != null) {
            text += if (pref.suffix.length > 1) " ${pref.suffix}" else pref.suffix
        }
        valueText.text = text
    }

    private fun adjustValue(direction: Int) {
        val seekBar = seekBar ?: return
        val pref = pref

        val currentProgress = seekBar.progress
        val newProgress: Int

        if (pref.isLogarithmic) {
            val currentBitrate = pref.linearToLog(currentProgress)
            val adjustStep = if (currentBitrate > 50000) pref.stepSize * 2 else pref.stepSize
            val newBitrate = maxOf(pref.minValue, minOf(pref.maxValue, currentBitrate + direction * adjustStep))
            newProgress = pref.logToLinear(newBitrate)
        } else {
            newProgress = maxOf(pref.minValue, minOf(pref.maxValue, currentProgress + direction * pref.stepSize))
        }

        seekBar.progress = newProgress
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLongPressView(view: View, direction: Int) {
        val handler = Handler(Looper.getMainLooper())
        val isLongPressing = booleanArrayOf(false)

        val repeatRunnable = object : Runnable {
            override fun run() {
                if (isLongPressing[0]) {
                    adjustValue(direction)
                    handler.postDelayed(this, LONG_PRESS_INTERVAL.toLong())
                }
            }
        }

        view.setOnClickListener {
            if (!isLongPressing[0]) {
                adjustValue(direction)
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressing[0] = false
                    handler.postDelayed({
                        isLongPressing[0] = true
                        adjustValue(direction)
                        handler.postDelayed(repeatRunnable, LONG_PRESS_INTERVAL.toLong())
                    }, LONG_PRESS_DELAY.toLong())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacksAndMessages(null)
                    isLongPressing[0] = false
                }
            }
            false
        }
    }

    private fun tintDialogButtons() {
        val alert = dialog as? AlertDialog ?: return
        AppDialogStyler.tintTitle(alert, requireContext())
        AppDialogStyler.installDismissKeys(alert)
        val accentColor = ContextCompat.getColor(requireContext(), R.color.app_dialog_accent_color)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { buttonId ->
                alert.getButton(buttonId)?.setTextColor(accentColor)
            }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult && seekBar != null) {
            val pref = pref
            val valueToSave = if (pref.isLogarithmic) pref.linearToLog(seekBar!!.progress) else seekBar!!.progress
            if (pref.callChangeListener(valueToSave)) {
                pref.setProgress(valueToSave)
            }
        }
    }

    companion object {
        private const val LONG_PRESS_DELAY = 400
        private const val LONG_PRESS_INTERVAL = 80

        fun newInstance(key: String): SeekBarPreferenceDialogFragment {
            return SeekBarPreferenceDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_KEY, key)
                }
            }
        }
    }
}
