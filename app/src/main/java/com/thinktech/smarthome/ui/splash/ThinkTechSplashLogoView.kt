package com.thinktech.smarthome.ui.splash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Native hardware-accelerated vector logo animation reproducing
 * "Logo ThinkTech – Splash saat aplikasi dibuka" (house + T cutout + wireless arcs).
 */
class ThinkTechSplashLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Exact easing curves from the HTML splash spec
    private val smoothOut = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val easeOut = PathInterpolator(0f, 0f, 0.58f, 1f)
    private val easeInOut = PathInterpolator(0.42f, 0f, 0.58f, 1f)

    // Paints
    private val housePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B4DFF")
        style = Paint.Style.FILL
    }

    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4F6FB")
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00A8D8")
        style = Paint.Style.FILL
    }

    private val arc1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00A8D8")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val arc2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00A8D8")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    // Exact SVG geometry in viewBox 0 0 68 62 coordinates
    private val housePath = Path().apply {
        moveTo(28.7f, 9f)
        quadTo(30f, 7.9f, 31.3f, 9f)
        lineTo(53f, 28f)
        quadTo(54f, 29f, 54f, 30.5f)
        lineTo(54f, 50f)
        arcTo(RectF(42f, 44f, 54f, 56f), 0f, 90f, false)
        lineTo(12f, 56f)
        arcTo(RectF(6f, 44f, 18f, 56f), 90f, 90f, false)
        lineTo(6f, 30.5f)
        quadTo(6f, 29f, 7f, 28f)
        close()
    }

    private val tBarRect = RectF(19f, 28.5f, 41f, 36f)
    private val tStemRect = RectF(26.25f, 28.5f, 33.75f, 48f)

    private val arc1Rect = RectF(42f, 8f, 58f, 24f)
    private val arc2Rect = RectF(35.5f, 1.5f, 64.5f, 30.5f)

    /**
     * Timeline percentage on the HTML's 0..100 keyframe scale.
     */
    var timelinePercent: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val pct = timelinePercent
        val scale = minOf(width / 68f, height / 62f)
        val dx = (width - 68f * scale) * 0.5f
        val dy = (height - 62f * scale) * 0.5f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        // 1. House body + T cutout group (hb: 10% -> 20% -> 25% -> 28%, pivot bottom-center (30, 56))
        val houseScale = evalHouseScale(pct)
        if (houseScale > 0.001f) {
            canvas.save()
            canvas.scale(houseScale, houseScale, 30f, 56f)
            canvas.drawPath(housePath, housePaint)

            // T horizontal bar (tb: 26% -> 32%, pivot left-center (19, 32.25))
            val tBarScaleX = evalSegment(pct, 26f, 32f, smoothOut)
            if (tBarScaleX > 0.001f) {
                canvas.save()
                canvas.scale(tBarScaleX, 1f, 19f, 32.25f)
                canvas.drawRoundRect(tBarRect, 3.75f, 3.75f, cutoutPaint)
                canvas.restore()
            }

            // T vertical stem (ts: 29% -> 36%, pivot top-center (30, 28.5))
            val tStemScaleY = evalSegment(pct, 29f, 36f, smoothOut)
            if (tStemScaleY > 0.001f) {
                canvas.save()
                canvas.scale(1f, tStemScaleY, 30f, 28.5f)
                canvas.drawRoundRect(tStemRect, 3.75f, 3.75f, cutoutPaint)
                canvas.restore()
            }
            canvas.restore()
        }

        // 2. Wireless Signal Dot (dt: 33% -> 37% -> 40%, po0 pulse)
        val dotScale = evalDotScale(pct)
        if (dotScale > 0.001f) {
            val dotAlpha = evalPulseOpacity(pct, offset = 0f)
            dotPaint.alpha = (dotAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.scale(dotScale, dotScale, 50f, 16f)
            canvas.drawCircle(50f, 16f, 3.5f, dotPaint)
            canvas.restore()
        }

        // 3. Inner Wireless Arc (a1: 36% -> 42%, po1 pulse)
        val arc1Progress = evalSegment(pct, 36f, 42f, smoothOut)
        if (arc1Progress > 0.001f) {
            val arc1Alpha = evalPulseOpacity(pct, offset = 2f)
            arc1Paint.alpha = (arc1Alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawArc(arc1Rect, -90f, 90f * arc1Progress, false, arc1Paint)
        }

        // 4. Outer Wireless Arc (a2: 40% -> 46%, po2 pulse)
        val arc2Progress = evalSegment(pct, 40f, 46f, smoothOut)
        if (arc2Progress > 0.001f) {
            val arc2Alpha = evalPulseOpacity(pct, offset = 4f)
            arc2Paint.alpha = (arc2Alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawArc(arc2Rect, -90f, 90f * arc2Progress, false, arc2Paint)
        }

        canvas.restore()
    }

    private fun evalSegment(pct: Float, start: Float, end: Float, interpolator: PathInterpolator): Float {
        if (pct <= start) return 0f
        if (pct >= end) return 1f
        return interpolator.getInterpolation((pct - start) / (end - start))
    }

    private fun evalHouseScale(pct: Float): Float {
        return when {
            pct <= 10f -> 0f
            pct <= 20f -> 1.08f * easeOut.getInterpolation((pct - 10f) / 10f)
            pct <= 25f -> 1.08f - 0.10f * easeOut.getInterpolation((pct - 20f) / 5f)
            pct <= 28f -> 0.98f + 0.02f * easeOut.getInterpolation((pct - 25f) / 3f)
            else -> 1f
        }
    }

    private fun evalDotScale(pct: Float): Float {
        return when {
            pct <= 33f -> 0f
            pct <= 37f -> 1.5f * easeOut.getInterpolation((pct - 33f) / 4f)
            pct <= 40f -> 1.5f - 0.5f * easeOut.getInterpolation((pct - 37f) / 3f)
            else -> 1f
        }
    }

    /**
     * Exact staggered pulse opacity matching CSS po0/po1/po2 keyframes:
     * Pulse 1: [50..54] 1 -> 0.25, [54..60] 0.25 -> 1
     * Pulse 2: [60..69] 1 -> 0.25, [69..75] 0.25 -> 1
     * Pulse 3: [75..84] 1 -> 0.25, [84..90] 0.25 -> 1
     */
    private fun evalPulseOpacity(pct: Float, offset: Float): Float {
        val p = pct - offset
        return when {
            p <= 50f -> 1f
            p <= 54f -> 1f - 0.75f * easeInOut.getInterpolation((p - 50f) / 4f)
            p <= 60f -> 0.25f + 0.75f * easeInOut.getInterpolation((p - 54f) / 6f)
            p <= 69f -> 1f - 0.75f * easeInOut.getInterpolation((p - 60f) / 9f)
            p <= 75f -> 0.25f + 0.75f * easeInOut.getInterpolation((p - 69f) / 6f)
            p <= 84f -> 1f - 0.75f * easeInOut.getInterpolation((p - 75f) / 9f)
            p <= 90f -> 0.25f + 0.75f * easeInOut.getInterpolation((p - 84f) / 6f)
            else -> 1f
        }
    }
}
