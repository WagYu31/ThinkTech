package com.thinktech.smarthome.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout

/**
 * Huawei HarmonyOS-inspired Light-Field Optical Card.
 * Renders a pure borderless squircle surface with silky hardware-accelerated radial
 * light-field illumination — zero clunky outlines or flat plastic boxes.
 */
class HarmonyLightFieldCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Mode {
        HERO_LIGHT_FIELD,
        DEVICE_ON,
        DEVICE_OFF
    }

    private val density = resources.displayMetrics.density
    private var cornerRadiusPx = 24f * density

    var lightFieldMode: Mode = Mode.HERO_LIGHT_FIELD
        set(value) {
            if (field != value) {
                field = value
                rebuildShaders()
                invalidate()
            }
        }

    fun setCornerRadiusDp(dp: Float) {
        cornerRadiusPx = dp * density
        invalidateOutline()
        invalidate()
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val primaryGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
    }

    private fun rebuildShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        when (lightFieldMode) {
            Mode.HERO_LIGHT_FIELD -> {
                // Soft HarmonyOS Blue top-right optical orb + subtle ThinkTech Cyan bloom
                primaryGlowPaint.shader = RadialGradient(
                    w * 0.92f,
                    h * 0.08f,
                    w * 0.72f,
                    intArrayOf(
                        Color.argb(38, 10, 89, 247),
                        Color.argb(14, 10, 89, 247),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
                secondaryGlowPaint.shader = RadialGradient(
                    w * 0.58f,
                    h * 0.0f,
                    w * 0.55f,
                    intArrayOf(
                        Color.argb(26, 0, 168, 216),
                        Color.argb(8, 0, 168, 216),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            Mode.DEVICE_ON -> {
                // Gentle radial glow radiating behind top-right circular power button
                primaryGlowPaint.shader = RadialGradient(
                    w * 0.84f,
                    h * 0.22f,
                    w * 0.85f,
                    intArrayOf(
                        Color.argb(30, 10, 89, 247),
                        Color.argb(10, 10, 89, 247),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                secondaryGlowPaint.shader = null
            }
            Mode.DEVICE_OFF -> {
                primaryGlowPaint.shader = null
                secondaryGlowPaint.shader = null
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, basePaint)

        if (primaryGlowPaint.shader != null) {
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, primaryGlowPaint)
        }
        if (secondaryGlowPaint.shader != null) {
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, secondaryGlowPaint)
        }
        if (lightFieldMode != Mode.DEVICE_OFF) {
            canvas.drawRoundRect(
                0.5f * density,
                0.5f * density,
                w - 0.5f * density,
                h - 0.5f * density,
                cornerRadiusPx,
                cornerRadiusPx,
                topSheenPaint
            )
        }
        super.onDraw(canvas)
    }
}
