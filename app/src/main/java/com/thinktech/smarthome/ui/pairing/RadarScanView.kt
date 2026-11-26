package com.thinktech.smarthome.ui.pairing

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Clean 3-ring concentric radar with gentle pulsing sonar waves matching modern flagship Add Device UX.
 */
class RadarScanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.25f * density
        color = Color.parseColor("#7C94FF")
    }

    private val midRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * density
        color = Color.parseColor("#AEBFFF")
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        color = Color.parseColor("#D6E0FF")
    }

    private val pulseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pulseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }

    private var pulsePhase = 0f
    private var isAnimating = false
    private var animator: ValueAnimator? = null

    init {
        startScan()
    }

    fun startScan() {
        isAnimating = true
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulsePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopScan() {
        isAnimating = false
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startScan()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopScan()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = (width.coerceAtMost(height) / 2f) - 4f * density
        if (maxRadius <= 0f) return

        // 1. Gentle dual sonar pulse ripples while actively scanning
        if (isAnimating) {
            drawPulseWave(canvas, cx, cy, maxRadius, pulsePhase)
            drawPulseWave(canvas, cx, cy, maxRadius, (pulsePhase + 0.5f) % 1f)
        }

        // 2. Three clean concentric architectural rings (inner boldest -> outer delicate)
        canvas.drawCircle(cx, cy, maxRadius * 0.44f, innerRingPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.72f, midRingPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.98f, outerRingPaint)
    }

    private fun drawPulseWave(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, phase: Float) {
        val startR = maxRadius * 0.24f
        val curR = startR + (maxRadius * 0.98f - startR) * phase
        val fade = (1f - phase) * (1f - phase)

        val fillAlpha = (24 * fade).toInt().coerceIn(0, 255)
        val strokeAlpha = (95 * fade).toInt().coerceIn(0, 255)

        pulseFillPaint.color = Color.argb(fillAlpha, 56, 88, 255)
        pulseStrokePaint.color = Color.argb(strokeAlpha, 56, 88, 255)

        canvas.drawCircle(cx, cy, curR, pulseFillPaint)
        canvas.drawCircle(cx, cy, curR, pulseStrokePaint)
    }
}
