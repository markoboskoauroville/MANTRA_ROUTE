package com.mantra.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.hypot

/**
 * The face of a Quick Settings tile: the channel letter above, the level below.
 *
 * TWO LINES, because one was wasting the height. On a single line the whole string "M100" is
 * four characters wide, and width was ALWAYS the binding constraint — measured, not assumed —
 * so the text could only reach 62px while more than half the circle's height sat empty. Split
 * across two lines the letter reaches 106px and the number 66px.
 *
 * Each line is grown against the CIRCLE at its own height. A line near the top or bottom has
 * less room than one through the middle, and the corner of its box is the point that runs out
 * first: half-width and half-height are the legs, the radius is the hypotenuse they must not
 * exceed. Sizing against the square instead is what clipped earlier attempts.
 */
object TileIcon {

    private const val SIZE = 192f
    private const val RADIUS = SIZE / 2f * 0.97f

    private const val LETTER_Y = SIZE * 0.30f
    private const val NUMBER_Y = SIZE * 0.69f

    private val cache = HashMap<String, Bitmap>()

    /** One size for every letter, so no channel is fatter than another. */
    private val letterSize: Float by lazy {
        growAt(Volume.STREAMS.map { TileText.one(it.label) }, LETTER_Y)
    }

    /** One size for every level, sized against the widest the number can ever be. */
    private val numberSize: Float by lazy { growAt(listOf("100"), NUMBER_Y) }

    fun render(letter: String, number: String): Bitmap {
        val key = "$letter|$number"
        cache[key]?.let { if (!it.isRecycled) return it }

        val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, letter, letterSize, LETTER_Y)
        draw(canvas, number, numberSize, NUMBER_Y)
        cache[key] = bitmap
        return bitmap
    }

    private fun draw(canvas: Canvas, text: String, size: Float, centreY: Float) {
        if (text.isEmpty()) return
        val paint = paint().apply { textSize = size }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        // Centre on the INK: the font's line box carries ascender and descender room that
        // capitals and digits never use, so centring on it sits visibly high.
        canvas.drawText(text, SIZE / 2f, centreY + bounds.height() / 2f, paint)
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    /** Largest size at which every one of [strings] keeps all four corners inside the circle. */
    private fun growAt(strings: List<String>, centreY: Float): Float {
        val paint = paint()
        val bounds = Rect()
        var size = 4f
        outer@ while (size < 300f) {
            paint.textSize = size + 1f
            for (text in strings) {
                paint.getTextBounds(text, 0, text.length, bounds)
                val halfW = bounds.width() / 2f
                val halfH = bounds.height() / 2f
                val top = centreY - halfH - SIZE / 2f
                val bottom = centreY + halfH - SIZE / 2f
                if (hypot(halfW, top) > RADIUS || hypot(halfW, bottom) > RADIUS) break@outer
            }
            size += 1f
        }
        return size
    }
}
