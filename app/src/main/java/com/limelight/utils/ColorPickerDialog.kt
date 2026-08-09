package com.limelight.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

import androidx.core.content.ContextCompat

import com.limelight.R
import kotlin.math.max
import kotlin.math.min

class ColorPickerDialog(
    context: Context,
    private val initialColor: Int,
    private val showAlphaSlider: Boolean,
    private val listener: OnColorSelectedListener?
) : Dialog(context) {

    fun interface OnColorSelectedListener {
        fun onColorSelected(color: Int)
    }

    private lateinit var svView: SaturationValueView
    private lateinit var hueSlider: HueSlider
    private lateinit var colorPreview: View
    private lateinit var hexInput: EditText
    private var alphaSeekBar: SeekBar? = null
    private lateinit var redSeekBar: SeekBar
    private lateinit var greenSeekBar: SeekBar
    private lateinit var blueSeekBar: SeekBar
    private var alphaValue: EditText? = null
    private lateinit var redValue: EditText
    private lateinit var greenValue: EditText
    private lateinit var blueValue: EditText

    private val currentHsv = FloatArray(3)
    private var currentAlpha: Int = 0
    private var isUpdatingFromInput = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        AppDialogStyler.installDismissKeys(this)

        Color.colorToHSV(initialColor, currentHsv)
        currentAlpha = Color.alpha(initialColor)

        val masterLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(
                color(R.color.crown_panel_background),
                dpToPx(14).toFloat(),
                color(R.color.crown_panel_border)
            )
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            gravity = Gravity.CENTER_VERTICAL
        }

        val pickerLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        svView = SaturationValueView(context)
        hueSlider = HueSlider(context)

        pickerLayout.addView(svView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            rightMargin = dpToPx(16)
        })
        pickerLayout.addView(hueSlider, LinearLayout.LayoutParams(dpToPx(24), ViewGroup.LayoutParams.MATCH_PARENT))

        masterLayout.addView(pickerLayout, LinearLayout.LayoutParams(0, dpToPx(220), 2f).apply {
            rightMargin = dpToPx(14)
        })

        val controlsScrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val controlsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                color(R.color.crown_section_background),
                dpToPx(10).toFloat(),
                color(R.color.crown_section_border)
            )
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
        }

        val previewHexLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(12))
        }

        colorPreview = View(context)
        previewHexLayout.addView(colorPreview, LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)))

        val hexContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(10), 0, 0, 0)
        }

        hexContainer.addView(TextView(context).apply {
            text = "HEX"
            textSize = 11f
            setTextColor(color(R.color.crown_text_secondary))
        })

        hexInput = EditText(context).apply {
            isSingleLine = true
            minEms = 9
            textSize = 13f
            setTextColor(color(R.color.crown_text_primary))
            setHintTextColor(color(R.color.crown_text_secondary))
            background = rounded(
                color(R.color.crown_input_background),
                dpToPx(8).toFloat(),
                color(R.color.crown_input_border)
            )
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
        }
        hexContainer.addView(hexInput)

        previewHexLayout.addView(hexContainer)
        controlsLayout.addView(previewHexLayout)

        if (showAlphaSlider) {
            controlsLayout.addView(createSliderRow("A:", 0, 255))
        }
        controlsLayout.addView(createSliderRow("R:", 1, 255))
        controlsLayout.addView(createSliderRow("G:", 2, 255))
        controlsLayout.addView(createSliderRow("B:", 3, 255))

        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }

        buttonLayout.addView(Button(context).apply {
            setText(R.string.game_menu_cancel)
            styleActionButton(this)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(0, dpToPx(38), 1f).apply {
            rightMargin = dpToPx(6)
        })
        buttonLayout.addView(Button(context).apply {
            setText(R.string.game_menu_ok)
            styleActionButton(this)
            setOnClickListener {
                listener?.onColorSelected(getColor())
                dismiss()
            }
        }, LinearLayout.LayoutParams(0, dpToPx(38), 1f).apply {
            leftMargin = dpToPx(6)
        })
        controlsLayout.addView(buttonLayout)

        controlsScrollView.addView(controlsLayout)
        masterLayout.addView(controlsScrollView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        setContentView(masterLayout)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.35f)

        setupListeners()
        updateAllComponents(initialColor)
    }

    private fun createSliderRow(label: String, componentIndex: Int, max: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }

        row.addView(TextView(context).apply {
            text = label
            minWidth = dpToPx(20)
            textSize = 12f
            setTextColor(color(R.color.crown_text_secondary))
        })

        val seekBar = SeekBar(context).apply {
            this.max = max
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(color(R.color.theme_pink_primary))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.crown_input_border))
                thumbTintList = android.content.res.ColorStateList.valueOf(color(R.color.theme_pink_primary))
            }
        }
        row.addView(seekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dpToPx(8)
            rightMargin = dpToPx(8)
        })

        val valueInput = EditText(context).apply {
            setText("0")
            maxLines = 1
            minWidth = dpToPx(44)
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(color(R.color.crown_text_primary))
            background = rounded(
                color(R.color.crown_input_background),
                dpToPx(8).toFloat(),
                color(R.color.crown_input_border)
            )
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
        }
        row.addView(valueInput)

        when (componentIndex) {
            0 -> { alphaSeekBar = seekBar; alphaValue = valueInput }
            1 -> { redSeekBar = seekBar; redValue = valueInput }
            2 -> { greenSeekBar = seekBar; greenValue = valueInput }
            3 -> { blueSeekBar = seekBar; blueValue = valueInput }
        }
        return row
    }

    private fun updateFromRgb() {
        val a = if (showAlphaSlider) alphaSeekBar!!.progress else 255
        val r = redSeekBar.progress
        val g = greenSeekBar.progress
        val b = blueSeekBar.progress
        val color = Color.argb(a, r, g, b)

        currentAlpha = a
        Color.colorToHSV(color, currentHsv)

        updatePreview(color)
        svView.setHue(currentHsv[0])
        svView.setSatVal(currentHsv[1], currentHsv[2])
        hueSlider.setHue(currentHsv[0])
        updateHexInput(color)
    }

    private fun setupListeners() {
        svView.setListener { sat, v -> updateFromSv(sat, v) }

        hueSlider.setListener { hue ->
            currentHsv[0] = hue
            svView.setHue(hue)
            updateFromHsv()
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (isUpdatingFromInput) return
                try {
                    var hexString = s.toString()
                    if (!hexString.startsWith("#")) {
                        hexString = "#$hexString"
                    }
                    var color = Color.parseColor(hexString)
                    if (s.length <= 7) {
                        color = (currentAlpha shl 24) or (color and 0x00FFFFFF)
                    }
                    updateAllComponents(color)
                } catch (_: IllegalArgumentException) {
                }
            }
        })

        setupComponentListeners(redSeekBar, redValue, 1)
        setupComponentListeners(greenSeekBar, greenValue, 2)
        setupComponentListeners(blueSeekBar, blueValue, 3)
        if (showAlphaSlider) {
            setupComponentListeners(alphaSeekBar!!, alphaValue!!, 0)
        }
    }

    private fun setupComponentListeners(seekBar: SeekBar, editText: EditText, componentIndex: Int) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    isUpdatingFromInput = true
                    editText.setText(progress.toString())
                    editText.setSelection(editText.text.length)
                    updateFromRgb()
                    isUpdatingFromInput = false
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (isUpdatingFromInput) return
                try {
                    val value = s.toString().toInt()
                    if (value in 0..255) {
                        isUpdatingFromInput = true
                        seekBar.progress = value
                        updateFromRgb()
                        isUpdatingFromInput = false
                    }
                } catch (_: NumberFormatException) {
                }
            }
        })
    }

    private fun updateFromSv(sat: Float, v: Float) {
        currentHsv[1] = sat
        currentHsv[2] = v
        updateFromHsv()
    }

    private fun updateFromHsv() {
        val color = Color.HSVToColor(currentAlpha, currentHsv)
        if (isUpdatingFromInput) return
        isUpdatingFromInput = true
        updateRgbControls(color)
        updateHexInput(color)
        updatePreview(color)
        isUpdatingFromInput = false
    }

    private fun updateAllComponents(color: Int) {
        Color.colorToHSV(color, currentHsv)
        currentAlpha = Color.alpha(color)

        isUpdatingFromInput = true
        updateRgbControls(color)
        updateHexInput(color)
        updatePreview(color)
        svView.setHue(currentHsv[0])
        svView.setSatVal(currentHsv[1], currentHsv[2])
        hueSlider.setHue(currentHsv[0])
        isUpdatingFromInput = false
    }

    private fun updateRgbControls(color: Int) {
        if (showAlphaSlider) {
            alphaSeekBar!!.progress = Color.alpha(color)
            alphaValue!!.setText(Color.alpha(color).toString())
        }
        redSeekBar.progress = Color.red(color)
        redValue.setText(Color.red(color).toString())
        greenSeekBar.progress = Color.green(color)
        greenValue.setText(Color.green(color).toString())
        blueSeekBar.progress = Color.blue(color)
        blueValue.setText(Color.blue(color).toString())
    }

    private fun updateHexInput(color: Int) {
        val hex = if (showAlphaSlider) {
            String.format("#%08x", color)
        } else {
            String.format("#%06x", 0xFFFFFF and color)
        }
        hexInput.setText(hex)
    }

    private fun updatePreview(color: Int) {
        colorPreview.background = rounded(
            color,
            dpToPx(10).toFloat(),
            color(R.color.crown_panel_border)
        )
    }

    private fun getColor(): Int {
        return Color.HSVToColor(currentAlpha, currentHsv)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun color(resId: Int): Int {
        return ContextCompat.getColor(context, resId)
    }

    private fun rounded(fillColor: Int, radius: Float, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius
            if (strokeColor != null) {
                setStroke(dpToPx(1), strokeColor)
            }
        }
    }

    private fun styleActionButton(button: Button) {
        button.isAllCaps = false
        button.textSize = 13f
        button.setTextColor(color(R.color.crown_text_primary))
        button.background = rounded(
            color(R.color.crown_input_background),
            dpToPx(8).toFloat(),
            color(R.color.crown_input_border)
        )
        button.minWidth = 0
        button.minimumWidth = 0
    }

    private class SaturationValueView(context: Context) : View(context) {
        private val satValPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = dpToPx(2).toFloat()
        }
        private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var hue = 0f
        private var saturation = 0f
        private var value = 1f
        private var selectorX = 0f
        private var selectorY = 0f
        private var listener: OnSVChangedListener? = null

        fun interface OnSVChangedListener {
            fun onColorChanged(sat: Float, `val`: Float)
        }

        fun setListener(listener: OnSVChangedListener) {
            this.listener = listener
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            updateShaders()
            updateSelectorPosition()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), satValPaint)
            selectorPaint.color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
            canvas.drawCircle(selectorX, selectorY, dpToPx(8).toFloat(), selectorPaint)
            canvas.drawCircle(selectorX, selectorY, dpToPx(8).toFloat(), strokePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    selectorX = max(0f, min(width.toFloat(), event.x))
                    selectorY = max(0f, min(height.toFloat(), event.y))
                    saturation = selectorX / width
                    value = 1 - selectorY / height
                    listener?.onColorChanged(saturation, value)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        fun setHue(hue: Float) {
            this.hue = hue
            updateShaders()
            invalidate()
        }

        fun setSatVal(sat: Float, v: Float) {
            this.saturation = sat
            this.value = v
            updateSelectorPosition()
            invalidate()
        }

        private fun updateShaders() {
            if (width <= 0 || height <= 0) return
            val pureColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            val saturationShader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, pureColor, Shader.TileMode.CLAMP)
            val valueShader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            satValPaint.shader = ComposeShader(valueShader, saturationShader, PorterDuff.Mode.SRC_OVER)
        }

        private fun updateSelectorPosition() {
            if (width <= 0 || height <= 0) return
            selectorX = saturation * width
            selectorY = (1 - value) * height
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        }
    }

    private class HueSlider(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shader: Shader? = null
        private var hue = 0f
        private var selectorY = 0f
        private var listener: OnHueChangedListener? = null

        fun interface OnHueChangedListener {
            fun onHueChanged(hue: Float)
        }

        fun setListener(listener: OnHueChangedListener) {
            this.listener = listener
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val hueColors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), hueColors, null, Shader.TileMode.CLAMP)
            updateSelectorPosition()
        }

        override fun onDraw(canvas: Canvas) {
            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpToPx(2).toFloat()
            paint.color = Color.WHITE

            val selectorRadius = width / 2f * 0.8f
            paint.style = Paint.Style.FILL
            paint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            canvas.drawCircle(width / 2f, selectorY, selectorRadius, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            canvas.drawCircle(width / 2f, selectorY, selectorRadius, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    selectorY = max(0f, min(height.toFloat(), event.y))
                    hue = selectorY / height * 360f
                    if (hue >= 360f) hue = 359.9f
                    listener?.onHueChanged(hue)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        fun setHue(hue: Float) {
            this.hue = hue
            updateSelectorPosition()
            invalidate()
        }

        private fun updateSelectorPosition() {
            if (height > 0) {
                selectorY = hue / 360f * height
            }
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        }
    }
}
