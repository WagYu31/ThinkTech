package com.thinktech.smarthome.ui.device

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Optical radial ambient light field for Huawei AI Life Smart Bulb Hero Card.
 * Smoothly adapts halo color and intensity to power state, RGB/CCT color, and brightness %.
 */
class HarmonyLightHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lightColor: Int = Color.parseColor("#FFB74D")
    private var brightnessFraction: Float = 0.85f
    private var isLightOn: Boolean = false

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    fun updateLight(isOn: Boolean, color: Int, brightnessPercent: Int) {
        isLightOn = isOn
        lightColor = color
        brightnessFraction = (brightnessPercent.coerceIn(5, 100)) / 100f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f
        if (maxR <= 0f) return

        val r = Color.red(lightColor)
        val g = Color.green(lightColor)
        val b = Color.blue(lightColor)

        if (isLightOn) {
            val peakAlpha = (70 + (125 * brightnessFraction)).toInt().coerceIn(60, 195)
            val midAlpha = (peakAlpha * 0.42f).toInt()
            val haloR = maxR * (0.72f + 0.28f * brightnessFraction)
            haloPaint.shader = RadialGradient(
                cx, cy, haloR,
                intArrayOf(
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(midAlpha, r, g, b),
                    Color.argb(0, r, g, b)
                ),
                floatArrayOf(0f, 0.54f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, haloR, haloPaint)

            coreRingPaint.color = Color.argb(140, r, g, b)
            canvas.drawCircle(cx, cy, maxR * 0.46f, coreRingPaint)
        } else {
            haloPaint.shader = RadialGradient(
                cx, cy, maxR * 0.72f,
                intArrayOf(Color.parseColor("#149398A2"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, maxR * 0.72f, haloPaint)
            coreRingPaint.color = Color.parseColor("#E4E8EF")
            canvas.drawCircle(cx, cy, maxR * 0.46f, coreRingPaint)
        }
    }
}
