package com.thinktech.smarthome.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flagship Huawei HarmonyOS NEXT-inspired Spatial Light-Field Hero Card.
 * Renders a deep luminous atmospheric gradient with subtly breathing optical aurora orbs,
 * architectural concentric spatial arcs, a volumetric light ribbon, and a specular glass rim.
 */
class HarmonyHomeHeroCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class HeroMood {
        ACTIVE,
        STANDBY,
        OFFLINE
    }

    private val density = resources.displayMetrics.density
    private var cornerRadiusPx = 28f * density
    private var breathPhase = 0f
    private var breathAnimator: ValueAnimator? = null

    var heroMood: HeroMood = HeroMood.ACTIVE
        set(value) {
            if (field != value) {
                field = value
                rebuildStaticShaders()
                invalidate()
            }
        }

    fun setCornerRadiusDp(dp: Float) {
        cornerRadiusPx = dp * density
        invalidateOutline()
        invalidate()
    }

    private val baseGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val depthOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val primaryAuroraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val secondarySheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val spatialRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
    }
    private val specularBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * density
    }
    private val ribbonPath = Path()

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBreathingAnimation()
    }

    override fun onDetachedFromWindow() {
        stopBreathingAnimation()
        super.onDetachedFromWindow()
    }

    private fun startBreathingAnimation() {
        if (breathAnimator?.isRunning == true) return
        breathAnimator = ValueAnimator.ofFloat(0f, (2.0 * PI).toFloat()).apply {
            duration = 9000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                breathPhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopBreathingAnimation() {
        breathAnimator?.cancel()
        breathAnimator = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildStaticShaders()
    }

    private fun rebuildStaticShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val baseColors = when (heroMood) {
            HeroMood.ACTIVE -> intArrayOf(
                Color.parseColor("#0A3ED6"),
                Color.parseColor("#1365F8"),
                Color.parseColor("#2AA2FF")
            )
            HeroMood.STANDBY -> intArrayOf(
                Color.parseColor("#10337A"),
                Color.parseColor("#1954BC"),
                Color.parseColor("#2A84DE")
            )
            HeroMood.OFFLINE -> intArrayOf(
                Color.parseColor("#1F3358"),
                Color.parseColor("#2C497B"),
                Color.parseColor("#3E659F")
            )
        }

        baseGradientPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            baseColors,
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )

        // Sculptural deep indigo depth anchor in bottom-left corner
        val depthColor = when (heroMood) {
            HeroMood.ACTIVE -> Color.argb(105, 18, 25, 128)
            HeroMood.STANDBY -> Color.argb(95, 12, 22, 86)
            HeroMood.OFFLINE -> Color.argb(90, 16, 24, 52)
        }
        depthOrbPaint.shader = RadialGradient(
            w * 0.08f,
            h * 1.05f,
            w * 0.65f,
            intArrayOf(depthColor, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // Diagonal luminous silk ribbon sweeping across upper-right quadrant
        ribbonPaint.shader = LinearGradient(
            w * 0.35f,
            0f,
            w,
            h * 0.72f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(26, 210, 244, 255),
                Color.argb(10, 130, 225, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )

        ribbonPath.reset()
        ribbonPath.moveTo(w * 0.28f, 0f)
        ribbonPath.cubicTo(w * 0.56f, h * 0.24f, w * 0.76f, h * 0.05f, w, h * 0.34f)
        ribbonPath.lineTo(w, h * 0.72f)
        ribbonPath.cubicTo(w * 0.74f, h * 0.44f, w * 0.52f, h * 0.58f, w * 0.16f, 0f)
        ribbonPath.close()

        // Specular top-to-bottom optical glass edge highlight
        specularBorderPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(
                Color.argb(128, 255, 255, 255),
                Color.argb(42, 255, 255, 255),
                Color.argb(18, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.onDraw(canvas)
            return
        }

        // 1. Base HarmonyOS spatial sapphire-azure canvas
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, baseGradientPaint)

        // 2. Sculptural bottom-left indigo depth shadow-bloom
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, depthOrbPaint)

        // 3. Gently breathing volumetric glacier-cyan aurora orb (top-right focal light)
        val driftX = cos(breathPhase) * 10f * density
        val driftY = sin(breathPhase) * 6f * density
        val orbAlpha = when (heroMood) {
            HeroMood.ACTIVE -> 118
            HeroMood.STANDBY -> 88
            HeroMood.OFFLINE -> 64
        }
        primaryAuroraPaint.shader = RadialGradient(
            w * 0.87f + driftX,
            h * 0.16f + driftY,
            w * 0.58f,
            intArrayOf(
                Color.argb(orbAlpha, 140, 240, 255),
                Color.argb(orbAlpha / 3, 72, 198, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, primaryAuroraPaint)

        // 4. Upper-center optical glass caustic highlight
        secondarySheenPaint.shader = RadialGradient(
            w * 0.40f - driftX * 0.5f,
            -h * 0.08f,
            w * 0.48f,
            intArrayOf(
                Color.argb(52, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, secondarySheenPaint)

        // 5. Silk optical wave ribbon
        canvas.drawPath(ribbonPath, ribbonPaint)

        // 6. Architectural concentric spatial rings (HarmonyOS Super Device signature motif)
        val cx = w * 0.86f
        val cy = h * 0.30f
        val pulse = sin(breathPhase) * 2.2f * density
        val ringRadii = floatArrayOf(46f * density + pulse, 82f * density + pulse * 1.2f, 122f * density + pulse * 1.4f)
        val ringAlphas = intArrayOf(38, 24, 13)
        for (i in ringRadii.indices) {
            spatialRingPaint.color = Color.argb(ringAlphas[i], 255, 255, 255)
            canvas.drawCircle(cx, cy, ringRadii[i], spatialRingPaint)
        }

        // 7. Precision specular glass rim
        val inset = 0.6f * density
        canvas.drawRoundRect(
            inset,
            inset,
            w - inset,
            h - inset,
            cornerRadiusPx,
            cornerRadiusPx,
            specularBorderPaint
        )

        super.onDraw(canvas)
    }
}
