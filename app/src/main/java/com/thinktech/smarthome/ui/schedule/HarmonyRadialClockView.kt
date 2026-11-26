package com.thinktech.smarthome.ui.schedule

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive 24-Hour & Minute Radial Analog Clock Face.
 * - HOUR_24 mode: Outer ring (1..12) + Inner ring (00, 13..23) with smooth royal-blue hand & badge.
 * - MINUTE mode: 00..55 ring with 1-minute drag precision.
 */
class HarmonyRadialClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ClockMode { HOUR_24, MINUTE }

    var onHourChanged: ((hour: Int) -> Unit)? = null
    var onHourCommitted: ((hour: Int) -> Unit)? = null
    var onMinuteChanged: ((minute: Int) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private var clockMode: ClockMode = ClockMode.HOUR_24
    private var selectedHour: Int = 21
    private var selectedMinute: Int = 45

    private var cx = 0f
    private var cy = 0f
    private var faceRadius = 0f
    private var outerNumRadius = 0f
    private var innerNumRadius = 0f

    private val faceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val faceBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.parseColor("#EAEEF5")
    }
    private val innerTrackRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        color = Color.parseColor("#F3F5FA")
    }
    private val handLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3B5BFF")
    }
    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3B5BFF")
    }
    private val badgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3B5BFF")
    }
    private val outerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#1D222D")
    }
    private val innerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#6B7280")
    }
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val tickDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D5DCE8")
    }

    fun setMode(mode: ClockMode) {
        clockMode = mode
        invalidate()
    }

    fun setTime(hour: Int, minute: Int) {
        selectedHour = hour.coerceIn(0, 23)
        selectedMinute = minute.coerceIn(0, 59)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w * 0.5f
        cy = h * 0.5f
        faceRadius = min(w, h) * 0.475f
        outerNumRadius = faceRadius * 0.80f
        innerNumRadius = faceRadius * 0.51f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faceRadius <= 0f) return

        // 1. Clean circular watch face with subtle luxury rim
        canvas.drawCircle(cx, cy, faceRadius, faceBgPaint)
        canvas.drawCircle(cx, cy, faceRadius, faceBorderPaint)
        if (clockMode == ClockMode.HOUR_24) {
            canvas.drawCircle(cx, cy, (outerNumRadius + innerNumRadius) * 0.5f, innerTrackRingPaint)
        }

        // 2. Compute selected selector position & angle
        val (selAngleDeg, selRadius) = if (clockMode == ClockMode.HOUR_24) {
            val isInner = selectedHour == 0 || selectedHour >= 13
            val hour12 = when {
                selectedHour == 0 -> 12
                selectedHour > 12 -> selectedHour - 12
                else -> selectedHour
            }
            val angle = (hour12 % 12) * 30f - 90f
            angle to (if (isInner) innerNumRadius else outerNumRadius)
        } else {
            val angle = selectedMinute * 6f - 90f
            angle to outerNumRadius
        }

        val selRad = Math.toRadians(selAngleDeg.toDouble())
        val sx = cx + selRadius * cos(selRad).toFloat()
        val sy = cy + selRadius * sin(selRad).toFloat()
        val badgeRadius = 21f * density

        // 3. Soft royal-blue glow halo behind selector circle + radial hand + center pivot
        badgeGlowPaint.shader = RadialGradient(
            sx, sy, badgeRadius * 1.48f,
            intArrayOf(Color.parseColor("#443B5BFF"), Color.TRANSPARENT),
            floatArrayOf(0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sx, sy, badgeRadius * 1.48f, badgeGlowPaint)

        canvas.drawLine(cx, cy, sx, sy, handLinePaint)
        canvas.drawCircle(cx, cy, 4.5f * density, pivotPaint)
        canvas.drawCircle(sx, sy, badgeRadius, badgeFillPaint)

        // 4. Draw dial numbers
        if (clockMode == ClockMode.HOUR_24) {
            drawHour24Numbers(canvas)
        } else {
            drawMinuteNumbers(canvas, sx, sy)
        }
    }

    private fun drawHour24Numbers(canvas: Canvas) {
        outerTextPaint.textSize = 14.5f * density
        innerTextPaint.textSize = 12.5f * density
        selectedTextPaint.textSize = 14.5f * density

        // Outer ring: 12, 1..11
        for (i in 0 until 12) {
            val value = if (i == 0) 12 else i
            val angleRad = Math.toRadians((i * 30.0) - 90.0)
            val tx = cx + outerNumRadius * cos(angleRad).toFloat()
            val ty = cy + outerNumRadius * sin(angleRad).toFloat()
            val isSelected = selectedHour == value
            val paint = if (isSelected) selectedTextPaint else outerTextPaint
            drawCenteredText(canvas, value.toString(), tx, ty, paint)
        }

        // Inner ring: 00, 13..23
        for (i in 0 until 12) {
            val value = if (i == 0) 0 else i + 12
            val label = if (value == 0) "00" else value.toString()
            val angleRad = Math.toRadians((i * 30.0) - 90.0)
            val tx = cx + innerNumRadius * cos(angleRad).toFloat()
            val ty = cy + innerNumRadius * sin(angleRad).toFloat()
            val isSelected = selectedHour == value
            val paint = if (isSelected) selectedTextPaint else innerTextPaint
            drawCenteredText(canvas, label, tx, ty, paint)
        }
    }

    private fun drawMinuteNumbers(canvas: Canvas, sx: Float, sy: Float) {
        outerTextPaint.textSize = 14f * density
        selectedTextPaint.textSize = 14f * density

        // Subtle minute ticks for non-multiple-of-5 positions
        for (m in 0 until 60) {
            if (m % 5 == 0) continue
            val rad = Math.toRadians((m * 6.0) - 90.0)
            val dx = cx + (faceRadius * 0.91f) * cos(rad).toFloat()
            val dy = cy + (faceRadius * 0.91f) * sin(rad).toFloat()
            canvas.drawCircle(dx, dy, 1.1f * density, tickDotPaint)
        }

        // 12 primary 5-minute labels: 00, 5, 10, ..., 55
        for (i in 0 until 12) {
            val value = i * 5
            val label = if (value == 0) "00" else value.toString()
            val angleRad = Math.toRadians((i * 30.0) - 90.0)
            val tx = cx + outerNumRadius * cos(angleRad).toFloat()
            val ty = cy + outerNumRadius * sin(angleRad).toFloat()
            val isSelected = selectedMinute == value
            val paint = if (isSelected) selectedTextPaint else outerTextPaint
            drawCenteredText(canvas, label, tx, ty, paint)
        }

        // If user dragged to an in-between minute (e.g. 47), show crisp white minute digits inside badge
        if (selectedMinute % 5 != 0) {
            selectedTextPaint.textSize = 13.5f * density
            drawCenteredText(canvas, String.format("%02d", selectedMinute), sx, sy, selectedTextPaint)
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val fm = paint.fontMetrics
        val baseline = y - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(text, x, baseline, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (dist > faceRadius * 1.08f) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(dx, dy, dist)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(dx, dy, dist)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateFromTouch(dx, dy, dist)
                if (clockMode == ClockMode.HOUR_24) {
                    onHourCommitted?.invoke(selectedHour)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(dx: Float, dy: Float, dist: Float) {
        // Angle clockwise from 12 o'clock (0°..360°)
        val degFromTop = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0).toFloat()

        if (clockMode == ClockMode.HOUR_24) {
            val step12 = ((degFromTop / 30f).roundToInt()) % 12
            val isInner = dist < (outerNumRadius + innerNumRadius) * 0.5f
            val newHour = if (isInner) {
                if (step12 == 0) 0 else step12 + 12
            } else {
                if (step12 == 0) 12 else step12
            }
            if (newHour != selectedHour) {
                selectedHour = newHour
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
                onHourChanged?.invoke(selectedHour)
            }
        } else {
            val newMinute = ((degFromTop / 6f).roundToInt()) % 60
            if (newMinute != selectedMinute) {
                selectedMinute = newMinute
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
                onMinuteChanged?.invoke(selectedMinute)
            }
        }
    }
}
