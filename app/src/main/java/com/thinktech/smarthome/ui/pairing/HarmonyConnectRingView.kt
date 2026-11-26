package com.thinktech.smarthome.ui.pairing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Smooth animated circular progress ring styled after Gambar 2 (27_Light_ad / 31_Light_add / 35_Light_ad).
 * Draws a crisp light-gray track with a royal blue rounded-cap progress arc starting at 12 o'clock (-90°).
 */
class HarmonyConnectRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.2f * density
        color = Color.parseColor("#ECEFF5")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.8f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3858FF")
    }

    private val glowTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#383858FF")
    }

    private val arcRect = RectF()
    private var progressValue: Float = 0f // 0f..100f

    fun setProgress(value: Float) {
        progressValue = value.coerceIn(0f, 100f)
        invalidate()
    }

    fun getProgress(): Float = progressValue

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val pad = 12f * density
        val cx = width / 2f
        val cy = height / 2f
        val radius = (size / 2f) - pad
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1. Base track circle
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // 2. Active sweeping blue arc
        val sweep = (progressValue / 100f) * 360f
        if (sweep > 0.5f) {
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint)

            // Soft optical glow at arc head while in progress
            if (progressValue in 1f..99f) {
                val endRad = Math.toRadians((-90f + sweep).toDouble())
                val tipX = cx + (cos(endRad) * radius).toFloat()
                val tipY = cy + (sin(endRad) * radius).toFloat()
                canvas.drawCircle(tipX, tipY, 7f * density, glowTipPaint)
            }
        }
    }
}
