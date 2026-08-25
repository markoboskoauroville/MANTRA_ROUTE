package com.mantra.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat

/**
 * The face of a Quick Settings tile, drawn as a bitmap.
 *
 * Quick Settings gives a tile one small square and, on this phone, draws nothing else — no
 * label, no subtitle. So everything the tile has to say has to fit inside that square.
 *
 * Three zones, top to bottom, asked for on 24.8.2026:
 *
 *     GLYPH    small, at the top — which channel, recognised by shape
 *     NUMBER   large, in the middle — the level, the thing you are actually reading
 *     NAME     four letters at the bottom — which channel, in words
 *
 * The channel is therefore said twice, in two different ways. That is the point rather than
 * redundancy: shape is faster once learned, letters are unambiguous while learning, and a
 * reader who finds one hard has the other.
 */
object TileIcon {

    private const val SIZE = 192f

    // Bands as fractions of the square. They sum to less than 1: the gaps are the margins,
    // and a number that touches the glyph above it is harder to read than a smaller one.
    private const val GLYPH_TOP = 0.06f
    private const val GLYPH_HEIGHT = 0.26f
    private const val NUMBER_BASELINE = 0.72f
    private const val NAME_BASELINE = 0.96f

    fun render(context: Context, glyphRes: Int, number: String, name: String): Bitmap {
        val size = SIZE.toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val glyphBox = (SIZE * GLYPH_HEIGHT).toInt()
        ContextCompat.getDrawable(context, glyphRes)?.let { drawable ->
            val left = (size - glyphBox) / 2
            val top = (SIZE * GLYPH_TOP).toInt()
            drawable.setTint(Color.WHITE)
            drawable.setBounds(left, top, left + glyphBox, top + glyphBox)
            drawable.draw(canvas)
        }

        // The number. Bold and large: it is what the tile is for.
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = SIZE * 0.40f
        }
        // Shrink to fit rather than clip: "100" is wider than "50" and must not lose a digit.
        fit(numberPaint, number, SIZE * 0.90f)
        canvas.drawText(number, SIZE / 2f, SIZE * NUMBER_BASELINE, numberPaint)

        // The name. Small, letter-spaced, and never more than four characters.
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            textSize = SIZE * 0.19f
            letterSpacing = 0.08f
        }
        fit(namePaint, name, SIZE * 0.94f)
        canvas.drawText(name, SIZE / 2f, SIZE * NAME_BASELINE, namePaint)

        return bitmap
    }

    /** Reduce the size until the text fits the width, with a floor so it never vanishes. */
    private fun fit(paint: Paint, text: String, maxWidth: Float) {
        while (paint.measureText(text) > maxWidth && paint.textSize > 8f) {
            paint.textSize -= 1f
        }
    }
}
