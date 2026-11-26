package com.thinktech.smarthome.ui.device

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Interactive 360° HSV Color Wheel styled after Huawei AI Life Smart Lighting Studio.
 * Emits smooth (hue: 0..360, saturation: 0..1, argbColor) callbacks during drag and release.
 */
class HarmonyColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#ECEFF3")
    }
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B0A192F")
    }
    private val thumbOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var wheelRadius = 0f

    private var currentHue = 220f
    private var currentSat = 0.78f

    var onColorChanged: ((hue: Float, sat: Float, color: Int, isFinal: Boolean) -> Unit)? = null

    fun setHsvColor(hue: Float, saturation: Float) {
        currentHue = ((hue % 360f) + 360f) % 360f
        currentSat = saturation.coerceIn(0f, 1f)
        invalidate()
    }

    fun getSelectedColor(): Int = Color.HSVToColor(floatArrayOf(currentHue, currentSat, 1f))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        wheelRadius = (min(w, h) / 2f) - 18f * density
        if (wheelRadius <= 0f) return

        val hueColors = intArrayOf(
            Color.RED,
            Color.MAGENTA,
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.YELLOW,
            Color.RED
        )
        huePaint.shader = SweepGradient(centerX, centerY, hueColors, null)
        satPaint.shader = RadialGradient(
            centerX,
            centerY,
            wheelRadius,
            Color.WHITE,
            0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (wheelRadius <= 0f) return

        // Draw smooth HSV disc
        canvas.drawCircle(centerX, centerY, wheelRadius, huePaint)
        canvas.drawCircle(centerX, centerY, wheelRadius, satPaint)
        canvas.drawCircle(centerX, centerY, wheelRadius, ringBorderPaint)

        // Compute thumb position (SweepGradient runs clockwise from 0° = 3 o'clock; HSV colors above are CCW-mapped so invert angle)
        val angleRad = Math.toRadians((-currentHue).toDouble())
        val dist = currentSat * wheelRadius
        val thumbX = centerX + (cos(angleRad) * dist).toFloat()
        val thumbY = centerY + (sin(angleRad) * dist).toFloat()

        // Draw floating lens thumb
        val outerR = 15f * density
        val innerR = 11f * density
        canvas.drawCircle(thumbX, thumbY + 2f * density, outerR + 1.5f * density, thumbShadowPaint)
        canvas.drawCircle(thumbX, thumbY, outerR, thumbOuterPaint)
        thumbInnerPaint.color = getSelectedColor()
        canvas.drawCircle(thumbX, thumbY, innerR, thumbInnerPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (wheelRadius <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                updateFromPoint(event.x, event.y, isFinal = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromPoint(event.x, event.y, isFinal = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateFromPoint(event.x, event.y, isFinal = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromPoint(x: Float, y: Float, isFinal: Boolean) {
        val dx = x - centerX
        val dy = y - centerY
        val dist = hypot(dx, dy).coerceAtMost(wheelRadius)
        val rawDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        currentHue = (((-rawDeg) % 360f) + 360f) % 360f
        currentSat = (dist / wheelRadius).coerceIn(0f, 1f)
        invalidate()
        onColorChanged?.invoke(currentHue, currentSat, getSelectedColor(), isFinal)
    }
}
