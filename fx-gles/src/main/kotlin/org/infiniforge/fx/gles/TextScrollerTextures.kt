package org.infiniforge.fx.gles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import org.infiniforge.fx.api.FxAlphaMode
import org.infiniforge.fx.api.FxColorEncoding
import org.infiniforge.fx.api.FxSampling
import org.infiniforge.fx.api.FxTexture
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Per-node, bounded bitmap-backed glyph textures for the built-in text generator. */
internal class TextScrollerTextures(private val pool: SurfacePool) : AutoCloseable {
    private val maxTextureSize = IntArray(1).also { GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, it, 0) }[0]
        .coerceAtMost(MAX_TEXT_TEXTURE_WIDTH)
    private var mainKey: MainKey? = null
    private var mainTexture: CachedTexture? = null
    private var labelKey: LabelKey? = null
    private var labelTexture: CachedTexture? = null

    fun main(value: String, family: String, outputHeight: Int, size: Float): FxTexture {
        val key = MainKey(value, family, outputHeight, size)
        if (key == mainKey) return checkNotNull(mainTexture).texture
        delete(mainTexture)
        mainTexture = null
        mainKey = null

        val fontSize = (outputHeight * size).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = fontSize
            typeface = typeface(family)
        }
        val rendered = value.replace('\n', ' ').replace('\r', ' ').ifEmpty { " " }
        val gap = (fontSize * .8f).roundToInt().coerceAtLeast(2)
        val measuredWidth = paint.measureText(rendered)
        val height = ceil(fontSize * 1.65f).toInt().coerceIn(2, maxTextureSize)
        val widthLimit = minOf(maxTextureSize, (MAX_TEXT_TEXTURE_BYTES / (height * 4L)).toInt()).coerceAtLeast(2)
        val width = ceil(measuredWidth + gap).toInt().coerceIn(2, widthLimit)
        val scaleX = if (measuredWidth + gap > widthLimit) {
            ((widthLimit - gap).coerceAtLeast(1) / measuredWidth.coerceAtLeast(1f)).coerceIn(.0001f, 1f)
        } else 1f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = (height - (paint.ascent() + paint.descent())) * .5f
        val x = gap * .5f
        paint.textScaleX = scaleX
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = max(1f, fontSize * .055f)
        paint.color = 0xffff3ed2.toInt()
        canvas.drawText(rendered, x, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(max(1f, fontSize * .11f), 0f, 0f, 0xff00eaff.toInt())
        paint.color = 0xffffffff.toInt()
        canvas.drawText(rendered, x, baseline, paint)
        paint.clearShadowLayer()
        mainTexture = upload(bitmap)
        mainKey = key
        return checkNotNull(mainTexture).texture
    }

    fun label(family: String, outputHeight: Int): FxTexture {
        val key = LabelKey(family, outputHeight)
        if (key == labelKey) return checkNotNull(labelTexture).texture
        delete(labelTexture)
        labelTexture = null
        labelKey = null

        val fontSize = (outputHeight * .055f).coerceIn(MIN_LABEL_SIZE, MAX_LABEL_SIZE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = fontSize
            typeface = typeface(family)
        }
        val title = "MTV Style"
        val paddingX = (fontSize * .45f).roundToInt().coerceAtLeast(4)
        val paddingY = (fontSize * .3f).roundToInt().coerceAtLeast(3)
        val width = ceil(paint.measureText(title)).toInt() + paddingX * 2
        val height = ceil(fontSize * 1.45f).toInt() + paddingY * 2
        val bitmap = Bitmap.createBitmap(width.coerceAtMost(maxTextureSize), height.coerceAtMost(maxTextureSize), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = paddingY - paint.ascent()
        val x = paddingX.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = 0xb8000000.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), fontSize * .24f, fontSize * .24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = max(2f, fontSize * .09f)
        paint.color = 0xff101010.toInt()
        canvas.drawText(title, x, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xffff45d4.toInt()
        canvas.drawText(title, x, baseline, paint)
        labelTexture = upload(bitmap)
        labelKey = key
        return checkNotNull(labelTexture).texture
    }

    override fun close() {
        delete(mainTexture)
        delete(labelTexture)
        mainTexture = null
        labelTexture = null
        mainKey = null
        labelKey = null
    }

    private fun upload(bitmap: Bitmap): CachedTexture {
        var reservedBytes = 0L
        var name = 0
        try {
            reservedBytes = pool.reserveExternalTexture(bitmap.width, bitmap.height)
            val names = IntArray(1)
            GLES30.glGenTextures(1, names, 0)
            name = names[0]
            check(name != 0) { "Unable to allocate text-scroller texture" }
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            return CachedTexture(
                FxTexture(
                    name = name,
                    width = bitmap.width,
                    height = bitmap.height,
                    alphaMode = FxAlphaMode.PREMULTIPLIED,
                    colorEncoding = FxColorEncoding.SRGB,
                    sampling = FxSampling.LINEAR,
                ),
                reservedBytes,
            )
        } catch (error: Throwable) {
            if (name != 0) GLES30.glDeleteTextures(1, intArrayOf(name), 0)
            if (reservedBytes > 0L) pool.releaseExternalTexture(reservedBytes)
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private fun delete(texture: CachedTexture?) {
        texture?.let {
            GLES30.glDeleteTextures(1, intArrayOf(it.texture.name), 0)
            pool.releaseExternalTexture(it.reservedBytes)
        }
    }

    private fun typeface(family: String) = Typeface.create(
        when (family) {
            "cursive" -> "cursive"
            "serif" -> "serif"
            "sans" -> "sans-serif"
            "mono" -> "monospace"
            else -> "casual"
        },
        Typeface.BOLD,
    )

    private data class MainKey(val value: String, val family: String, val outputHeight: Int, val size: Float)
    private data class LabelKey(val family: String, val outputHeight: Int)
    private data class CachedTexture(val texture: FxTexture, val reservedBytes: Long)

    private companion object {
        const val MAX_TEXT_TEXTURE_WIDTH = 8192
        const val MAX_TEXT_TEXTURE_BYTES = 16L * 1024L * 1024L
        const val MIN_FONT_SIZE = 12f
        const val MAX_FONT_SIZE = 2048f
        const val MIN_LABEL_SIZE = 18f
        const val MAX_LABEL_SIZE = 128f
    }
}
