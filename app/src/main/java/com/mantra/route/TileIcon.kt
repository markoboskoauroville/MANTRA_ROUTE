package com.mantra.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The face of a Quick Settings tile.
 *
 * Three rows, asked for on 25.8.2026: the RANGE the tile toggles between, the CURRENT value
 * large in the middle, and the four-letter channel name. Spread across the whole face rather
 * than huddled in the centre.
 *
 * THE GLYPH IS GONE. It took the top quarter to say the same thing the four letters below it
 * already said, leaving the number — the only part anyone actually reads — competing for what
 * was left. Two ways of naming the channel was affordable with two rows; with three it is a
 * decoration paid for in legibility.
 *
 * THE SQUARE IS NOT THE SURFACE. A tile is a CIRCLE, so the usable width at any height is the
 * chord at that height, not the width of the bitmap. The middle row may run edge to edge
 * because the circle is widest there; the top and bottom rows may not, and sizing them against
 * the square is exactly how letters get clipped at the corners.
 */
object TileIcon {

    private const val SIZE = 192f

    private const val RANGE_Y = 0.185f
    private const val RANGE_H = 0.16f
    private const val VALUE_Y = 0.52f
    private const val VALUE_H = 0.42f
    private const val NAME_Y = 0.865f
    private const val NAME_H = 0.15f

    private const val EDGE = 0.92f
    private const val EDGE_MIDDLE = 0.99f

    fun render(range: String, value: String, name: String): Bitmap {
        val size = SIZE.toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        row(canvas, range, RANGE_Y, RANGE_H, EDGE)
        row(canvas, value, VALUE_Y, VALUE_H, EDGE_MIDDLE)
        row(canvas, name, NAME_Y, NAME_H, EDGE)
        return bitmap
    }

    /** Usable width at height [y] as a fraction of the square, for a circle inscribed in it. */
    private fun chord(y: Float): Float {
        val d = abs(y - 0.5f)
        if (d >= 0.5f) return 0f
        return 2f * sqrt(0.25f - d * d)
    }

    private fun row(canvas: Canvas, text: String, y: Float, height: Float, edge: Float) {
        if (text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        grow(paint, text, SIZE * chord(y) * edge, SIZE * height)

        // Centre on the INK, not the font's line box: the box carries ascender and descender
        // room that digits and capitals never use, so centring on it sits visibly high.
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, SIZE / 2f, SIZE * y + bounds.height() / 2f, paint)
    }

    /**
     * Raise the size until the text hits its width or its height.
     *
     * GROWN, not shrunk. "50" is two characters and "100" is three; starting large and shrinking
     * leaves the short string at whatever size the long one needed.
     */
    private fun grow(paint: Paint, text: String, maxWidth: Float, maxHeight: Float) {
        var size = 4f
        val bounds = Rect()
        while (size < 220f) {
            paint.textSize = size + 1f
            paint.getTextBounds(text, 0, text.length, bounds)
            if (paint.measureText(text) > maxWidth || bounds.height() > maxHeight) break
            size += 1f
        }
        paint.textSize = size
    }
}
