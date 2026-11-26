package com.thinktech.smarthome.ui.device

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Flagship Smart Lamp Interactive Dial featuring:
 * 1. WHITE_CCT mode: 270° sculpted warm-to-cool horseshoe arc with outer ticks & live Kelvin pill.
 * 2. COLOR_RGB mode: 360° seamless precision RGB spectrum ring with 28 chronograph radial ticks.
 * 3. Centerpiece: Vector-precision 3D shaded frosted-glass A60 Smart Bulb with metallic E27 screw
 *    base and dynamic live ambient aura glow that responds to power, color, saturation, & brightness.
 * Tapping the center bulb toggles power; dragging the ring smoothly updates Hue or Kelvin.
 */
class HarmonyLampDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DialMode { WHITE_CCT, COLOR_RGB }

    var onHueChanged: ((hue: Float, isFinal: Boolean) -> Unit)? = null
    var onCctChanged: ((cctProgress: Int, isFinal: Boolean) -> Unit)? = null
    var onBulbTapped: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private var mode: DialMode = DialMode.COLOR_RGB
    private var isPowerOn: Boolean = true
    private var currentHue: Float = 218f          // 0..360
    private var currentSaturation: Float = 0.85f  // 0..1
    private var currentBrightness: Int = 85       // 5..100
    private var currentCctProgress: Int = 40      // 0 (2700K warm) .. 100 (6500K cool)

    private var activeDragMode = DragTarget.NONE

    private enum class DragTarget { NONE, RING, BULB }

    // Reusable Paints & Geometry
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#0D18243E")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val innerGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#ECEFF5")
        strokeWidth = 1.1f * density
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val kelvinPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F4F6FA")
    }
    private val kelvinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // 3D Smart Bulb Paints
    private val bulbGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulbTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulbHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulbOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.85f * density
        color = Color.parseColor("#180F172A")
    }
    private val bulbCollarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulbMetalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulbGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#6B7280")
    }
    private val bulbTipPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringRect = RectF()
    private val bulbPath = Path()
    private val collarPath = Path()
    private val specPath = Path()
    private val shaderMatrix = Matrix()

    // Cached geometry
    private var cx = 0f
    private var cy = 0f
    private var ringRadius = 0f
    private var ringStroke = 0f

    // Mathematically exact 30° HSV spectrum matching angle = (90° + hue) % 360°
    // (12 o'clock = Cyan 180°, 2 o'clock = Royal Blue 240°, 6 o'clock = Red 0°, 10 o'clock = Green 120°)
    private val rgbRingColors = intArrayOf(
        Color.parseColor("#8000FF"), // 0°   (hue 270° Violet)
        Color.parseColor("#FF00FF"), // 30°  (hue 300° Magenta)
        Color.parseColor("#FF007F"), // 60°  (hue 330° Rose)
        Color.parseColor("#FF0000"), // 90°  (hue 0°   Red - bottom)
        Color.parseColor("#FF8000"), // 120° (hue 30°  Orange)
        Color.parseColor("#FFEE00"), // 150° (hue 60°  Yellow)
        Color.parseColor("#80FF00"), // 180° (hue 90°  Lime)
        Color.parseColor("#00E626"), // 210° (hue 120° Green)
        Color.parseColor("#00F58A"), // 240° (hue 150° Mint)
        Color.parseColor("#00E5FF"), // 270° (hue 180° Cyan - top)
        Color.parseColor("#007FFF"), // 300° (hue 210° Azure)
        Color.parseColor("#2638FF"), // 330° (hue 240° Royal Blue)
        Color.parseColor("#8000FF")  // 360° (hue 270° Violet -> ZERO SEAM!)
    )
    private val rgbRingStops = floatArrayOf(
        0.000f, 0.083f, 0.167f, 0.250f, 0.333f, 0.417f,
        0.500f, 0.583f, 0.667f, 0.750f, 0.833f, 0.917f, 1.000f
    )

    // Hue (0..360 HSV) mapped smoothly to ring angle (in degrees, 0=3 o'clock clockwise)
    private fun hueToRingAngle(hue: Float): Float {
        // Red (hue 0°) sits at bottom (90°), Green (hue 120°) sits at left-top (210°), Blue (hue 240°) sits at top-right (330°)
        return (90f + hue) % 360f
    }

    private fun ringAngleToHue(angleDeg: Float): Float {
        return ((angleDeg - 90f) % 360f + 360f) % 360f
    }

    // CCT arc: 135° (bottom-left warm 2700K) clockwise 270° to 405° (45° bottom-right cool 6500K)
    private fun cctToArcAngle(progress: Int): Float {
        return 135f + (progress.coerceIn(0, 100) / 100f) * 270f
    }

    private fun arcAngleToCct(angleDeg: Float): Int {
        // Normalize angle relative to 135° clockwise
        val rel = ((angleDeg - 135f) % 360f + 360f) % 360f
        return when {
            rel <= 270f -> ((rel / 270f) * 100f).toInt().coerceIn(0, 100)
            rel < 315f -> 100 // Closer to cool end (45°)
            else -> 0         // Closer to warm end (135°)
        }
    }

    fun setDialState(
        dialMode: DialMode,
        powerOn: Boolean,
        hue: Float,
        saturation: Float,
        brightnessPct: Int,
        cctProgress: Int
    ) {
        mode = dialMode
        isPowerOn = powerOn
        currentHue = ((hue % 360f) + 360f) % 360f
        currentSaturation = saturation.coerceIn(0f, 1f)
        currentBrightness = brightnessPct.coerceIn(5, 100)
        currentCctProgress = cctProgress.coerceIn(0, 100)
        invalidate()
    }

    private fun computeActiveGlowColor(): Int {
        return if (mode == DialMode.COLOR_RGB) {
            Color.HSVToColor(floatArrayOf(currentHue, currentSaturation.coerceAtLeast(0.18f), 1f))
        } else {
            interpolateCctColor(currentCctProgress / 100f)
        }
    }

    private fun computeRingThumbColor(): Int {
        return if (mode == DialMode.COLOR_RGB) {
            Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
        } else {
            interpolateCctArcColor(currentCctProgress / 100f)
        }
    }

    private fun interpolateCctColor(t: Float): Int {
        val warm = Color.parseColor("#FFB648")
        val neutral = Color.parseColor("#FFF0D4")
        val cool = Color.parseColor("#9BC5FF")
        return if (t < 0.5f) blend(warm, neutral, t * 2f) else blend(neutral, cool, (t - 0.5f) * 2f)
    }

    private fun interpolateCctArcColor(t: Float): Int {
        val warm = Color.parseColor("#FFD37A")
        val mid = Color.parseColor("#FFF9EC")
        val cool = Color.parseColor("#B6CEFF")
        return if (t < 0.5f) blend(warm, mid, t * 2f) else blend(mid, cool, (t - 0.5f) * 2f)
    }

    private fun blend(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.rgb(
            (Color.red(c1) * inv + Color.red(c2) * ratio).toInt(),
            (Color.green(c1) * inv + Color.green(c2) * ratio).toInt(),
            (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w * 0.5f
        cy = h * 0.5f
        val minDim = min(w, h).toFloat()
        ringStroke = (minDim * 0.122f).coerceIn(24f * density, 34f * density)
        ringRadius = minDim * 0.365f
        ringRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val glowColor = computeActiveGlowColor()
        val brightFactor = currentBrightness / 100f

        // 1. Whisper-soft live ambient glow radiating behind the bulb inside the dial
        if (isPowerOn) {
            val auraRadius = ringRadius * 0.88f
            val coreAlpha = (36 + (62 * brightFactor)).toInt().coerceIn(28, 108)
            val midAlpha = (14 + (26 * brightFactor)).toInt().coerceIn(10, 44)
            auraPaint.shader = RadialGradient(
                cx, cy - 6f * density, auraRadius,
                intArrayOf(
                    Color.argb(coreAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                    Color.argb(midAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.58f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy - 6f * density, auraRadius, auraPaint)
        }

        // 2. Delicate inner circular guide line surrounding the smart bulb
        canvas.drawCircle(cx, cy, ringRadius * 0.62f, innerGuidePaint)

        // 3. Draw Outer Precision Ticks & Ring / Horseshoe Arc
        if (mode == DialMode.COLOR_RGB) {
            drawOuterRadialTicks(canvas, fullCircle = true, activeAngleDeg = hueToRingAngle(currentHue))
            drawRgbSpectrumRing(canvas)
            drawThumb(canvas, hueToRingAngle(currentHue), computeRingThumbColor())
        } else {
            drawOuterRadialTicks(canvas, fullCircle = false, activeAngleDeg = cctToArcAngle(currentCctProgress))
            drawCctHorseshoeArc(canvas)
            drawThumb(canvas, cctToArcAngle(currentCctProgress), computeRingThumbColor())
            drawKelvinCenterBadge(canvas)
        }

        // 4. Centerpiece Photorealistic 3D Smart Bulb
        draw3DSmartBulb(canvas, cx, cy - 2f * density, ringRadius * 0.44f, glowColor, brightFactor)
    }

    private fun drawOuterRadialTicks(canvas: Canvas, fullCircle: Boolean, activeAngleDeg: Float) {
        val tickCount = 28
        val innerR = ringRadius + ringStroke * 0.5f + 8f * density
        val outerR = innerR + 5.5f * density
        val majorOuterR = innerR + 8f * density

        for (i in 0 until tickCount) {
            val angleDeg = (i.toFloat() / tickCount) * 360f
            if (!fullCircle) {
                // Skip bottom open mouth (between 54° and 126°) for 270° CCT horseshoe
                if (angleDeg in 54f..126f) continue
            }
            val rad = Math.toRadians(angleDeg.toDouble())
            val isCardinal = (i % 7 == 0)
            val delta = kotlin.math.abs(((angleDeg - activeAngleDeg + 540f) % 360f) - 180f)
            val nearThumb = delta < 16f

            tickPaint.strokeWidth = if (nearThumb || isCardinal) 1.5f * density else 1.05f * density
            tickPaint.color = when {
                nearThumb && isPowerOn -> Color.parseColor("#3B5BFF")
                isCardinal -> Color.parseColor("#B6BFCE")
                else -> Color.parseColor("#D5DBE5")
            }

            val r2 = if (isCardinal) majorOuterR else outerR
            canvas.drawLine(
                cx + innerR * cos(rad).toFloat(),
                cy + innerR * sin(rad).toFloat(),
                cx + r2 * cos(rad).toFloat(),
                cy + r2 * sin(rad).toFloat(),
                tickPaint
            )
        }
    }

    private fun drawRgbSpectrumRing(canvas: Canvas) {
        // Subtle elevation shadow beneath ring
        ringShadowPaint.strokeWidth = ringStroke + 2f * density
        canvas.drawCircle(cx, cy + 2f * density, ringRadius, ringShadowPaint)

        // Seamless closed 360° SweepGradient ring
        ringPaint.strokeWidth = ringStroke
        ringPaint.shader = SweepGradient(cx, cy, rgbRingColors, rgbRingStops)
        ringPaint.alpha = if (isPowerOn) 255 else 135
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
    }

    private fun drawCctHorseshoeArc(canvas: Canvas) {
        // Soft elevation shadow beneath horseshoe
        ringShadowPaint.strokeWidth = ringStroke + 2f * density
        canvas.drawArc(ringRect, 135f, 270f, false, ringShadowPaint)

        // Anchor SweepGradient seam at 90° (bottom empty mouth) so both rounded endcaps stay pure:
        // 135° (0.125) = Warm 2700K Gold, 270° (0.500) = Pure White, 45° (0.875) = Cool 6500K Sky
        val cctSweep = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.parseColor("#FFCF6E"), // 0.000 -> 90°  (empty mouth warm side)
                Color.parseColor("#FFCF6E"), // 0.125 -> 135° (warm endcap 100% pure gold)
                Color.parseColor("#FFE5A6"), // 0.280 -> warm-soft
                Color.parseColor("#FFFDF7"), // 0.500 -> 270° (top pure white)
                Color.parseColor("#DCE8FF"), // 0.720 -> cool-soft
                Color.parseColor("#B4CEFF"), // 0.875 -> 45°  (cool endcap 100% pure sky)
                Color.parseColor("#B4CEFF")  // 1.000 -> 90°  (empty mouth cool side)
            ),
            floatArrayOf(0.0f, 0.125f, 0.28f, 0.50f, 0.72f, 0.875f, 1.0f)
        )
        shaderMatrix.reset()
        shaderMatrix.setRotate(90f, cx, cy)
        cctSweep.setLocalMatrix(shaderMatrix)

        ringPaint.strokeWidth = ringStroke
        ringPaint.shader = cctSweep
        ringPaint.alpha = if (isPowerOn) 255 else 150
        canvas.drawArc(ringRect, 135f, 270f, false, ringPaint)
    }

    private fun drawThumb(canvas: Canvas, angleDeg: Float, fillColor: Int) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val tx = cx + ringRadius * cos(rad).toFloat()
        val ty = cy + ringRadius * sin(rad).toFloat()
        val thumbR = ringStroke * 0.56f

        // Soft diffused elevation shadow under thumb
        thumbShadowPaint.shader = RadialGradient(
            tx, ty + 2f * density, thumbR * 1.48f,
            intArrayOf(Color.parseColor("#380F172A"), Color.TRANSPARENT),
            floatArrayOf(0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(tx, ty + 2f * density, thumbR * 1.48f, thumbShadowPaint)

        // Colored interior fill
        thumbFillPaint.color = if (isPowerOn) fillColor else Color.parseColor("#C7CDD8")
        canvas.drawCircle(tx, ty, thumbR, thumbFillPaint)

        // Crisp pure-white outer ring border
        thumbBorderPaint.strokeWidth = 3.6f * density
        canvas.drawCircle(tx, ty, thumbR, thumbBorderPaint)
    }

    private fun drawKelvinCenterBadge(canvas: Canvas) {
        val kelvin = 2700 + (currentCctProgress * 38)
        val label = when {
            kelvin < 3300 -> "Hangat"
            kelvin < 4800 -> "Netral"
            else -> "Sejuk"
        }
        val text = "${kelvin}K • $label"
        kelvinTextPaint.textSize = 11.5f * scaledDensity
        kelvinTextPaint.color = if (isPowerOn) Color.parseColor("#3B5BFF") else Color.parseColor("#8A909E")

        val textW = kelvinTextPaint.measureText(text)
        val pillW = textW + 24f * density
        val pillH = 26f * density
        val pillCy = cy + ringRadius * 0.88f
        val pillRect = RectF(cx - pillW / 2f, pillCy - pillH / 2f, cx + pillW / 2f, pillCy + pillH / 2f)

        kelvinPillPaint.color = if (isPowerOn) Color.parseColor("#EEF2FF") else Color.parseColor("#F1F3F7")
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, kelvinPillPaint)
        canvas.drawText(text, cx, pillCy + 4f * density, kelvinTextPaint)
    }

    /**
     * Draws a sculpted studio-lit 3D A60 frosted-glass Smart Bulb with dynamic live color illumination
     * and brushed metallic E27 screw threads.
     */
    private fun draw3DSmartBulb(
        canvas: Canvas,
        bx: Float,
        by: Float,
        size: Float,
        glowColor: Int,
        brightFactor: Float
    ) {
        // Vertical layout metrics centered on (bx, by)
        val domeR = size * 0.56f
        val domeCy = by - size * 0.24f
        val neckTopY = domeCy + domeR * 0.74f
        val neckBottomY = neckTopY + size * 0.34f
        val screwBottomY = neckBottomY + size * 0.31f

        val neckTopHalfW = domeR * 0.67f
        val neckBotHalfW = domeR * 0.43f

        // 1. Frosted Glass Dome Silhouette Path
        bulbPath.reset()
        bulbPath.moveTo(bx - neckTopHalfW, neckTopY)
        bulbPath.cubicTo(
            bx - domeR * 1.06f, domeCy + domeR * 0.36f,
            bx - domeR * 1.06f, domeCy - domeR * 0.92f,
            bx, domeCy - domeR
        )
        bulbPath.cubicTo(
            bx + domeR * 1.06f, domeCy - domeR * 0.92f,
            bx + domeR * 1.06f, domeCy + domeR * 0.36f,
            bx + neckTopHalfW, neckTopY
        )
        bulbPath.close()

        // Pearlescent 3D Frosted Glass Base Shading
        bulbGlassPaint.shader = RadialGradient(
            bx - domeR * 0.28f,
            domeCy - domeR * 0.30f,
            domeR * 1.45f,
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F4F6FA"),
                Color.parseColor("#E3E7F0")
            ),
            floatArrayOf(0.0f, 0.58f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(bulbPath, bulbGlassPaint)

        // Dynamic Internal Color Glow inside Glass Dome when Powered ON
        if (isPowerOn) {
            val tintCenterAlpha = (70 + (115 * brightFactor)).toInt().coerceIn(55, 195)
            val tintEdgeAlpha = (28 + (65 * brightFactor)).toInt().coerceIn(22, 100)
            bulbTintPaint.shader = RadialGradient(
                bx, domeCy - domeR * 0.08f, domeR * 1.08f,
                intArrayOf(
                    Color.argb(tintCenterAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                    Color.argb(tintEdgeAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.72f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(bulbPath, bulbTintPaint)
        }

        // Studio Softbox Glossy Reflection Highlight Crescent (top-left of glass dome)
        specPath.reset()
        specPath.addOval(
            bx - domeR * 0.62f,
            domeCy - domeR * 0.68f,
            bx - domeR * 0.14f,
            domeCy - domeR * 0.06f,
            Path.Direction.CW
        )
        canvas.save()
        canvas.rotate(-22f, bx - domeR * 0.38f, domeCy - domeR * 0.37f)
        bulbHighlightPaint.shader = LinearGradient(
            bx - domeR * 0.62f, domeCy - domeR * 0.68f,
            bx - domeR * 0.14f, domeCy - domeR * 0.06f,
            intArrayOf(Color.parseColor("#CCFFFFFF"), Color.parseColor("#10FFFFFF")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(specPath, bulbHighlightPaint)
        canvas.restore()

        canvas.drawPath(bulbPath, bulbOutlinePaint)

        // 2. Sculpted Matte Ceramic Collar / Heat-Sink Housing
        collarPath.reset()
        collarPath.moveTo(bx - neckTopHalfW, neckTopY)
        collarPath.lineTo(bx + neckTopHalfW, neckTopY)
        collarPath.cubicTo(
            bx + neckTopHalfW * 0.92f, neckTopY + (neckBottomY - neckTopY) * 0.55f,
            bx + neckBotHalfW * 1.12f, neckBottomY - 2f * density,
            bx + neckBotHalfW, neckBottomY
        )
        collarPath.lineTo(bx - neckBotHalfW, neckBottomY)
        collarPath.cubicTo(
            bx - neckBotHalfW * 1.12f, neckBottomY - 2f * density,
            bx - neckTopHalfW * 0.92f, neckTopY + (neckBottomY - neckTopY) * 0.55f,
            bx - neckTopHalfW, neckTopY
        )
        collarPath.close()

        bulbCollarPaint.shader = LinearGradient(
            bx - neckTopHalfW, neckTopY, bx + neckTopHalfW, neckTopY,
            intArrayOf(
                Color.parseColor("#D8DDE8"),
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#EEF1F7"),
                Color.parseColor("#C8CFD9")
            ),
            floatArrayOf(0.0f, 0.32f, 0.68f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(collarPath, bulbCollarPaint)
        canvas.drawPath(collarPath, bulbOutlinePaint)

        // 3. Brushed Chrome E27 Screw Base
        val screwW = neckBotHalfW * 0.86f
        val screwRect = RectF(bx - screwW, neckBottomY, bx + screwW, screwBottomY - size * 0.06f)
        bulbMetalPaint.shader = LinearGradient(
            bx - screwW, neckBottomY, bx + screwW, neckBottomY,
            intArrayOf(
                Color.parseColor("#7D8596"),
                Color.parseColor("#E5E9F0"),
                Color.parseColor("#B6BECB"),
                Color.parseColor("#6B7280")
            ),
            floatArrayOf(0.0f, 0.34f, 0.66f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(screwRect, 3f * density, 3f * density, bulbMetalPaint)

        // Helical screw threads (3 diagonal precision grooves)
        bulbGroovePaint.strokeWidth = 1.4f * density
        val threadStep = (screwRect.height()) / 3.8f
        for (i in 1..3) {
            val ty = neckBottomY + i * threadStep
            canvas.drawLine(
                bx - screwW * 0.92f, ty - 1.2f * density,
                bx + screwW * 0.92f, ty + 1.2f * density,
                bulbGroovePaint
            )
        }

        // Bottom black ceramic insulator & metallic contact nub
        bulbTipPaint.color = Color.parseColor("#2B303A")
        val tipRect = RectF(
            bx - screwW * 0.56f,
            screwRect.bottom - 2f * density,
            bx + screwW * 0.56f,
            screwBottomY + 2f * density
        )
        canvas.drawRoundRect(tipRect, 4f * density, 4f * density, bulbTipPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val innerHit = ringRadius - ringStroke * 0.95f
                val outerHit = ringRadius + ringStroke * 1.15f
                return when {
                    dist in innerHit..outerHit -> {
                        activeDragMode = DragTarget.RING
                        parent?.requestDisallowInterceptTouchEvent(true)
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        handleRingTouch(dx, dy, isFinal = false)
                        true
                    }
                    dist < innerHit -> {
                        activeDragMode = DragTarget.BULB
                        true
                    }
                    else -> false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeDragMode == DragTarget.RING) {
                    handleRingTouch(dx, dy, isFinal = false)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val prev = activeDragMode
                activeDragMode = DragTarget.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                if (prev == DragTarget.RING) {
                    handleRingTouch(dx, dy, isFinal = true)
                    return true
                } else if (prev == DragTarget.BULB && event.actionMasked == MotionEvent.ACTION_UP) {
                    if (dist < ringRadius - ringStroke * 0.7f) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onBulbTapped?.invoke()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleRingTouch(dx: Float, dy: Float, isFinal: Boolean) {
        val rawDeg = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() % 360f) + 360f) % 360f
        if (mode == DialMode.COLOR_RGB) {
            currentHue = ringAngleToHue(rawDeg)
            invalidate()
            onHueChanged?.invoke(currentHue, isFinal)
        } else {
            currentCctProgress = arcAngleToCct(rawDeg)
            invalidate()
            onCctChanged?.invoke(currentCctProgress, isFinal)
        }
    }
}
