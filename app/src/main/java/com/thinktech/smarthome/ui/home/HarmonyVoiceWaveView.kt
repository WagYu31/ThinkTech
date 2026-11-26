package com.thinktech.smarthome.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Full-width edge-to-edge mirrored multi-color organic Gaussian wave spectrum.
 * Matches the crisp modern voice assistant wave aesthetic (orange, violet, sky blue,
 * cobalt blue centerpiece, fresh lime green) with live SpeechRecognizer RMS reactivity.
 */
class HarmonyVoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class WaveLobe(
        val centerNorm: Float,
        val sigmaNorm: Float,
        val heightFactor: Float,
        val topColors: IntArray,
        val bottomColors: IntArray,
        val phaseOffset: Float,
        val speedMul: Float,
        val alpha: Int
    )

    // Back-to-front draw order: outer lobes first, vibrant hero center cobalt peak on top
    private val lobes = listOf(
        // Far-left edge lobe (top: warm orange, bottom: vivid violet)
        WaveLobe(
            centerNorm = 0.015f,
            sigmaNorm = 0.055f,
            heightFactor = 0.17f,
            topColors = intArrayOf(Color.parseColor("#FF8818"), Color.parseColor("#FFC340")),
            bottomColors = intArrayOf(Color.parseColor("#7C2AE8"), Color.parseColor("#B55CFF")),
            phaseOffset = 0.2f,
            speedMul = 1.2f,
            alpha = 235
        ),
        // Far-right edge lobe (top: vivid violet, bottom: warm orange)
        WaveLobe(
            centerNorm = 0.985f,
            sigmaNorm = 0.055f,
            heightFactor = 0.17f,
            topColors = intArrayOf(Color.parseColor("#7C2AE8"), Color.parseColor("#B55CFF")),
            bottomColors = intArrayOf(Color.parseColor("#FF8818"), Color.parseColor("#FFC340")),
            phaseOffset = 2.4f,
            speedMul = 1.15f,
            alpha = 235
        ),
        // Mid-left lobe (top: vivid violet, bottom: warm golden orange)
        WaveLobe(
            centerNorm = 0.165f,
            sigmaNorm = 0.078f,
            heightFactor = 0.29f,
            topColors = intArrayOf(Color.parseColor("#7A2BF0"), Color.parseColor("#AE4BFF")),
            bottomColors = intArrayOf(Color.parseColor("#FF8E1C"), Color.parseColor("#FFC93C")),
            phaseOffset = 0.9f,
            speedMul = 1.35f,
            alpha = 238
        ),
        // Mid-right lobe (top: warm golden orange, bottom: vivid violet)
        WaveLobe(
            centerNorm = 0.835f,
            sigmaNorm = 0.078f,
            heightFactor = 0.29f,
            topColors = intArrayOf(Color.parseColor("#FF8E1C"), Color.parseColor("#FFC93C")),
            bottomColors = intArrayOf(Color.parseColor("#7A2BF0"), Color.parseColor("#AE4BFF")),
            phaseOffset = 3.1f,
            speedMul = 1.3f,
            alpha = 238
        ),
        // Inner-left lobe (top: sky cyan blue, bottom: fresh lime green)
        WaveLobe(
            centerNorm = 0.33f,
            sigmaNorm = 0.056f,
            heightFactor = 0.58f,
            topColors = intArrayOf(Color.parseColor("#2B86FF"), Color.parseColor("#54CCFF")),
            bottomColors = intArrayOf(Color.parseColor("#68BC2F"), Color.parseColor("#A4E04E")),
            phaseOffset = 1.7f,
            speedMul = 1.5f,
            alpha = 244
        ),
        // Inner-right lobe (top: fresh lime green, bottom: sky cyan blue)
        WaveLobe(
            centerNorm = 0.67f,
            sigmaNorm = 0.056f,
            heightFactor = 0.58f,
            topColors = intArrayOf(Color.parseColor("#68BC2F"), Color.parseColor("#A4E04E")),
            bottomColors = intArrayOf(Color.parseColor("#2B86FF"), Color.parseColor("#54CCFF")),
            phaseOffset = 4.0f,
            speedMul = 1.45f,
            alpha = 244
        ),
        // Hero center cobalt-blue peak (top & bottom rich luminous cobalt)
        WaveLobe(
            centerNorm = 0.50f,
            sigmaNorm = 0.068f,
            heightFactor = 0.99f,
            topColors = intArrayOf(Color.parseColor("#3555F8"), Color.parseColor("#5374FF")),
            bottomColors = intArrayOf(Color.parseColor("#3555F8"), Color.parseColor("#4F6FFF")),
            phaseOffset = 0.0f,
            speedMul = 1.6f,
            alpha = 246
        )
    )

    private val lobePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()

    private var phase = 0f
    private var targetAmplitude = 0.72f
    private var smoothedAmplitude = 0.72f
    private var isListening = false
    private var animator: ValueAnimator? = null

    fun setListening(active: Boolean) {
        isListening = active
        targetAmplitude = if (active) 0.84f else 0.68f
        startAnim()
        invalidate()
    }

    fun updateRmsDb(rmsDb: Float) {
        if (!isListening) return
        val norm = ((rmsDb + 2f) / 10f).coerceIn(0.62f, 1.0f)
        targetAmplitude = norm
    }

    private fun startAnim() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, (2.0 * PI).toFloat()).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                smoothedAmplitude += (targetAmplitude - smoothedAmplitude) * 0.16f
                invalidate()
            }
            start()
        }
    }

    fun stopAnim() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnim()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnim()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ribbonPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(
                Color.parseColor("#998E44EC"),
                Color.parseColor("#AA3898FF"),
                Color.parseColor("#CC3552F6"),
                Color.parseColor("#AA7AC943"),
                Color.parseColor("#99FF9428")
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cy = h * 0.5f
        val maxHalfH = h * 0.46f

        // 1. Soft translucent silky center bridge ribbon for continuous flow across edges
        drawSoftSilkRibbon(canvas, w, cy, maxHalfH)

        // 2. Top & bottom mirrored organic multi-color Gaussian lobes
        for (lobe in lobes) {
            val breatheTop = if (isListening) {
                0.86f + 0.14f * sin(phase * lobe.speedMul + lobe.phaseOffset)
            } else {
                0.92f + 0.08f * sin(phase + lobe.phaseOffset)
            }
            val breatheBottom = if (isListening) {
                0.86f + 0.14f * sin(phase * lobe.speedMul + lobe.phaseOffset + 0.7f)
            } else {
                0.92f + 0.08f * sin(phase + lobe.phaseOffset + 0.5f)
            }

            val driftX = sin(phase + lobe.phaseOffset) * (if (isListening) 0.011f else 0.005f)
            val topPeak = maxHalfH * lobe.heightFactor * smoothedAmplitude * breatheTop
            val bottomPeak = maxHalfH * lobe.heightFactor * smoothedAmplitude * breatheBottom

            // Subtle translucent skirt layer behind each lobe for glossy overlap depth
            drawGaussianLobe(
                canvas = canvas,
                w = w,
                cy = cy,
                centerNorm = lobe.centerNorm + driftX * 0.6f,
                sigmaNorm = lobe.sigmaNorm * 1.24f,
                peakHeight = topPeak * 0.44f,
                direction = -1f,
                colors = lobe.topColors,
                alpha = 44
            )
            drawGaussianLobe(
                canvas = canvas,
                w = w,
                cy = cy,
                centerNorm = lobe.centerNorm - driftX * 0.6f,
                sigmaNorm = lobe.sigmaNorm * 1.24f,
                peakHeight = bottomPeak * 0.44f,
                direction = 1f,
                colors = lobe.bottomColors,
                alpha = 44
            )

            // Primary crisp vibrant top & bottom lobes
            drawGaussianLobe(
                canvas = canvas,
                w = w,
                cy = cy,
                centerNorm = lobe.centerNorm + driftX,
                sigmaNorm = lobe.sigmaNorm,
                peakHeight = topPeak,
                direction = -1f,
                colors = lobe.topColors,
                alpha = lobe.alpha
            )
            drawGaussianLobe(
                canvas = canvas,
                w = w,
                cy = cy,
                centerNorm = lobe.centerNorm - driftX,
                sigmaNorm = lobe.sigmaNorm,
                peakHeight = bottomPeak,
                direction = 1f,
                colors = lobe.bottomColors,
                alpha = lobe.alpha
            )
        }
    }

    private fun drawSoftSilkRibbon(canvas: Canvas, w: Float, cy: Float, maxHalfH: Float) {
        val steps = 64
        val ribbonHalf = maxHalfH * 0.075f * smoothedAmplitude
        path.reset()
        path.moveTo(0f, cy)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = t * w
            val wave = sin(t * PI * 4.0 + phase).toFloat() * 0.35f + 0.65f
            val y = cy - ribbonHalf * wave
            path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val t = i.toFloat() / steps
            val x = t * w
            val wave = sin(t * PI * 4.0 - phase).toFloat() * 0.35f + 0.65f
            val y = cy + ribbonHalf * wave
            path.lineTo(x, y)
        }
        path.close()
        ribbonPaint.alpha = 95
        canvas.drawPath(path, ribbonPaint)
    }

    private fun drawGaussianLobe(
        canvas: Canvas,
        w: Float,
        cy: Float,
        centerNorm: Float,
        sigmaNorm: Float,
        peakHeight: Float,
        direction: Float,
        colors: IntArray,
        alpha: Int
    ) {
        if (peakHeight <= 1f) return
        val span = (sigmaNorm * 3.1f)
        val startNorm = (centerNorm - span).coerceAtLeast(-0.04f)
        val endNorm = (centerNorm + span).coerceAtMost(1.04f)
        val segments = 40

        path.reset()
        path.moveTo(startNorm * w, cy)
        for (i in 0..segments) {
            val t = startNorm + (endNorm - startNorm) * (i.toFloat() / segments)
            val d = (t - centerNorm) / sigmaNorm
            // Smooth raised bell envelope
            val g = exp((-d * d * 0.92).toDouble()).toFloat()
            val y = cy + direction * peakHeight * g
            path.lineTo(t * w, y)
        }
        path.lineTo(endNorm * w, cy)
        path.close()

        lobePaint.shader = LinearGradient(
            (centerNorm - sigmaNorm) * w,
            cy + direction * peakHeight,
            (centerNorm + sigmaNorm) * w,
            cy,
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        lobePaint.alpha = alpha
        canvas.drawPath(path, lobePaint)
    }
}
