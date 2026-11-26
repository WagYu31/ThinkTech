package com.thinktech.smarthome.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.thinktech.smarthome.R
import kotlin.math.max

/**
 * Huawei Whole-Home Smart (全屋智能) & HarmonyOS UCD Editorial Hero Card.
 * Combines a sunlit architectural interior photograph on the right with a feathered
 * porcelain-alabaster optical veil on the left for effortless high-contrast typography,
 * whisper-quiet state tinting, and precision 0.6dp rim light.
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
    private var cornerRadiusPx = 26f * density

    var heroMood: HeroMood = HeroMood.ACTIVE
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

    private val bgBitmap: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.img_harmony_home_hero)
        }.getOrNull()
    }

    private val bitmapMatrix = Matrix()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val moodWashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bottomMistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.75f * density
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
        rebuildBitmapMatrix()
        rebuildShaders()
    }

    private fun rebuildBitmapMatrix() {
        val bmp = bgBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Scale 1.18x anchored flush-right so the raw wall seam sits safely behind the
        // opaque porcelain veil (x ~ 0.41w) while the olive vase, cove light, and sheer
        // linen curtains emerge seamlessly from morning light on the right half.
        val scale = max(w / bmp.width.toFloat(), h / bmp.height.toFloat()) * 1.18f
        val scaledW = bmp.width * scale
        val scaledH = bmp.height * scale
        val dx = w - scaledW
        val dy = (h - scaledH) * 0.40f

        bitmapMatrix.reset()
        bitmapMatrix.postScale(scale, scale)
        bitmapMatrix.postTranslate(dx, dy)
    }

    private fun rebuildShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Seamless porcelain-alabaster silk veil fully covering x <= 0.43w (zero hard seam)
        // then feathering organically into the sunlit architectural room on the right.
        veilPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            0f,
            intArrayOf(
                Color.argb(255, 255, 255, 255),
                Color.argb(255, 254, 254, 255),
                Color.argb(170, 252, 253, 255),
                Color.argb(38, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.43f, 0.54f, 0.66f, 0.78f),
            Shader.TileMode.CLAMP
        )

        // Subtle atmospheric daylight tone depending on home activity state
        val washColor = when (heroMood) {
            HeroMood.ACTIVE -> Color.argb(14, 255, 244, 224)   // Warm sunlit equilibrium
            HeroMood.STANDBY -> Color.argb(14, 226, 238, 255)  // Calm morning mist
            HeroMood.OFFLINE -> Color.argb(48, 236, 240, 245)  // Quiet desaturated standby
        }
        moodWashPaint.color = washColor

        // Gentle bottom porcelain lift so the frosted dock sits harmoniously over the floor
        bottomMistPaint.shader = LinearGradient(
            0f,
            h * 0.60f,
            0f,
            h,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(90, 250, 251, 253)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // Precision 0.75dp inner specular rim (subtle white top sheen -> delicate neutral edge)
        rimPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(
                Color.argb(235, 255, 255, 255),
                Color.argb(110, 255, 255, 255),
                Color.argb(22, 15, 23, 42)
            ),
            floatArrayOf(0f, 0.45f, 1f),
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

        // 1. Pure porcelain base
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, basePaint)

        // 2. Architectural interior photography (clipped cleanly to squircle outline)
        bgBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, bitmapMatrix, bitmapPaint)
        }

        // 3. Feathered porcelain silk veil over left half + seam dissolution
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, veilPaint)

        // 4. Gentle bottom lift under unified frosted telemetry dock
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, bottomMistPaint)

        // 5. Whisper-subtle mood daylight wash
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, moodWashPaint)

        // 6. Precision hairline specular border
        val inset = 0.4f * density
        canvas.drawRoundRect(
            inset,
            inset,
            w - inset,
            h - inset,
            cornerRadiusPx,
            cornerRadiusPx,
            rimPaint
        )

        super.onDraw(canvas)
    }
}
