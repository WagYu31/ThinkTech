package com.thinktech.smarthome.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Luminous 3D Aurora / Liquid-Glass Voice Assistant Sphere Orb.
 * Renders a deep sapphire glass orb with soft outer cyan halo glow, swirling iridescent
 * cyan/magenta/violet silk-fiber aurora waves, and a brilliant white-cyan core flare that
 * pulses dynamically in response to SpeechRecognizer voice levels.
 */
class HarmonyVoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sphereBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plumePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fiberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val coreFlarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val fiberPath = Path()

    private var phase = 0f
    private var isListening = false
    private var targetEnergy = 0.35f
    private var smoothedEnergy = 0.35f
    private var animator: ValueAnimator? = null

    fun setListening(active: Boolean) {
        isListening = active
        targetEnergy = if (active) 0.72f else 0.32f
        startAnim()
        invalidate()
    }

    fun updateRmsDb(rmsDb: Float) {
        if (!isListening) return
        targetEnergy = ((rmsDb + 2f) / 10f).coerceIn(0.45f, 1.0f)
    }

    private fun startAnim() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, (2.0 * PI).toFloat()).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                smoothedEnergy += (targetEnergy - smoothedEnergy) * 0.16f
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val pulse = 1f + sin(phase * 2.0).toFloat() * (if (isListening) 0.035f * smoothedEnergy else 0.015f)
        val sphereRadius = min(w, h) * 0.36f * pulse
        val haloRadius = sphereRadius * 1.38f

        // 1. Soft Ethereal Outer Cyan-Blue Halo Glow on pure white canvas
        haloPaint.shader = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(
                Color.parseColor("#6638BDF8"),
                Color.parseColor("#443B82F6"),
                Color.parseColor("#1A60A5FA"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.55f, 0.74f, 0.88f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, haloRadius, haloPaint)

        // 2. Clip inside the 3D glass sphere
        clipPath.reset()
        clipPath.addCircle(cx, cy, sphereRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Deep cosmic sapphire base gradient
        sphereBasePaint.shader = RadialGradient(
            cx - sphereRadius * 0.14f,
            cy - sphereRadius * 0.14f,
            sphereRadius * 1.15f,
            intArrayOf(
                Color.parseColor("#162364"),
                Color.parseColor("#0A1242"),
                Color.parseColor("#13378F"),
                Color.parseColor("#2B8CFF")
            ),
            floatArrayOf(0.0f, 0.45f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, sphereRadius, sphereBasePaint)

        val swirl = phase * (if (isListening) 1.4f else 0.8f)

        // 3. Soft Glowing Aurora Nebula Plumes (Turquoise Top-Right + Magenta Bottom-Left + Indigo)
        drawNebulaPlume(
            canvas = canvas,
            px = cx + sphereRadius * (0.28f + 0.10f * cos(swirl)),
            py = cy - sphereRadius * (0.24f + 0.08f * sin(swirl)),
            radius = sphereRadius * 0.78f,
            innerColor = Color.parseColor("#CC38E8FF"),
            midColor = Color.parseColor("#661E90FF")
        )
        drawNebulaPlume(
            canvas = canvas,
            px = cx - sphereRadius * (0.16f + 0.08f * sin(swirl * 1.1f)),
            py = cy + sphereRadius * (0.26f + 0.08f * cos(swirl)),
            radius = sphereRadius * 0.74f,
            innerColor = Color.parseColor("#CCEA64C8"),
            midColor = Color.parseColor("#558B5CF6")
        )
        drawNebulaPlume(
            canvas = canvas,
            px = cx - sphereRadius * 0.34f,
            py = cy - sphereRadius * 0.12f,
            radius = sphereRadius * 0.62f,
            innerColor = Color.parseColor("#9938BDF8"),
            midColor = Color.parseColor("#332563EB")
        )

        // 4. Fine Iridescent Silk-Mesh Light Fibers curving smoothly across the orb core
        drawAuroraFiberFan(
            canvas = canvas,
            cx = cx,
            cy = cy,
            r = sphereRadius,
            swirl = swirl,
            startColor = Color.parseColor("#80E8FF"),
            endColor = Color.parseColor("#FFFFFF"),
            bendSign = -1f,
            lineCount = 24
        )
        drawAuroraFiberFan(
            canvas = canvas,
            cx = cx,
            cy = cy,
            r = sphereRadius,
            swirl = -swirl * 0.85f + 1.2f,
            startColor = Color.parseColor("#F472B6"),
            endColor = Color.parseColor("#7DD3FC"),
            bendSign = 1f,
            lineCount = 18
        )

        // 5. Diagonal Brilliant Luminous White-Cyan Equator Core Light Flare
        canvas.save()
        canvas.rotate(-14f + sin(swirl) * 6f, cx, cy)
        val flareW = sphereRadius * (0.86f + 0.08f * smoothedEnergy)
        val flareH = sphereRadius * (0.20f + 0.06f * smoothedEnergy)
        coreFlarePaint.shader = RadialGradient(
            cx, cy, flareW,
            intArrayOf(
                Color.parseColor("#FFFFFFFF"),
                Color.parseColor("#E6C8F4FF"),
                Color.parseColor("#88E879F9"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.28f, 0.60f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.scale(1.0f, flareH / flareW, cx, cy)
        canvas.drawCircle(cx, cy, flareW, coreFlarePaint)
        canvas.restore()
        canvas.restore()

        // 6. Luminous Cyan-Blue Glass Rim Highlight & Subtle Top Gloss
        rimPaint.shader = RadialGradient(
            cx, cy, sphereRadius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.parseColor("#4438BDF8"),
                Color.parseColor("#CC67E8F9")
            ),
            floatArrayOf(0.0f, 0.72f, 0.90f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, sphereRadius, rimPaint)

        canvas.restore()
    }

    private fun drawNebulaPlume(
        canvas: Canvas,
        px: Float,
        py: Float,
        radius: Float,
        innerColor: Int,
        midColor: Int
    ) {
        plumePaint.shader = RadialGradient(
            px, py, radius,
            intArrayOf(innerColor, midColor, Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.52f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(px, py, radius, plumePaint)
    }

    private fun drawAuroraFiberFan(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        swirl: Float,
        startColor: Int,
        endColor: Int,
        bendSign: Float,
        lineCount: Int
    ) {
        fiberPaint.shader = LinearGradient(
            cx - r, cy + r * 0.2f,
            cx + r, cy - r * 0.4f,
            intArrayOf(Color.TRANSPARENT, startColor, endColor, Color.TRANSPARENT),
            floatArrayOf(0.05f, 0.36f, 0.66f, 0.96f),
            Shader.TileMode.CLAMP
        )

        val baseStroke = 0.62f * density
        for (i in 0 until lineCount) {
            val t = (i.toFloat() / (lineCount - 1)) - 0.5f // -0.5 .. +0.5
            val spread = t * r * (0.98f + 0.14f * smoothedEnergy)
            val waveShift = sin(swirl + i * 0.18f) * r * 0.11f

            val startX = cx - r * 0.96f
            val startY = cy + r * 0.24f + spread * 0.34f
            val endX = cx + r * 0.96f
            val endY = cy - r * 0.20f + spread * 0.54f

            val ctrl1X = cx - r * 0.22f
            val ctrl1Y = cy + bendSign * (r * 0.28f + spread * 0.86f) + waveShift
            val ctrl2X = cx + r * 0.28f
            val ctrl2Y = cy + bendSign * (r * 0.54f + spread * 0.96f) - waveShift

            fiberPath.reset()
            fiberPath.moveTo(startX, startY)
            fiberPath.cubicTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, endX, endY)

            val centerBoost = 1f - kotlin.math.abs(t) * 1.55f
            fiberPaint.strokeWidth = baseStroke * (0.65f + 0.55f * centerBoost.coerceAtLeast(0.2f))
            fiberPaint.alpha = (28 + (112 * centerBoost.coerceAtLeast(0.12f))).toInt().coerceIn(22, 145)
            canvas.drawPath(fiberPath, fiberPaint)
        }
    }
}
