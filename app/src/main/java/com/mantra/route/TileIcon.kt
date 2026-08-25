package com.mantra.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.ContextCompat

/**
 * The face of a Quick Settings tile, drawn as a bitmap.
 *
 * Quick Settings gives a tile ONE small square and draws nothing else on this phone — no label,
 * no subtitle. Whatever the tile has to say has to fit inside that square, and the square is
 * then scaled down into the circle, so every pixel of margin is magnified into wasted space.
 *
 * v18: the text now reaches the edges. v17 kept polite margins that looked right at 192px and
 * vanished once the system shrank the icon into the tile. The number is measured and scaled to
 * the full width, and the bands below sum to the whole square with nothing spare.
 */
object TileIcon {

    private const val SIZE = 192f

    // Bands as fractions of the square, summing to 1.0. No decorative gaps: at tile size a gap
    // is invisible as a gap and visible only as smaller text.
    private const val GLYPH_TOP = 0.00f
    private const val GLYPH_HEIGHT = 0.22f
    private const val NUMBER_BAND_TOP = 0.22f
    private const val NUMBER_BAND_HEIGHT = 0.54f
    private const val NAME_BASELINE = 0.98f

    // The number sits at the vertical MIDDLE, where a circular mask is at its widest, so it can
    // run the full width safely. The name sits at the bottom, where a circle has closed in to
    // roughly 44% of the width — so it is held back.
    //
    // NOT VERIFIED ON A DEVICE: whether Quick Settings masks a tile icon to a circle at all. If
    // it does not, the name could be wider than this. Erring narrow costs a few points of size;
    // erring wide loses letters, which is not recoverable by looking.
    private const val NUMBER_MAX_WIDTH = 0.98f
    private const val NAME_MAX_WIDTH = 0.72f

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

        // The number, grown until it touches both edges or fills its band, whichever binds
        // first. GROWN rather than shrunk: "50" is two digits and "100" is three, and starting
        // large and shrinking would leave "50" at the size that only "100" needed.
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        grow(numberPaint, number, SIZE * NUMBER_MAX_WIDTH, SIZE * NUMBER_BAND_HEIGHT)
        val bounds = Rect()
        numberPaint.getTextBounds(number, 0, number.length, bounds)
        // Centre the ink of the digits in the band, not the font's line box: the box carries
        // ascender and descender room that digits never use, and centring on it sits high.
        val bandCentre = SIZE * (NUMBER_BAND_TOP + NUMBER_BAND_HEIGHT / 2f)
        canvas.drawText(number, SIZE / 2f, bandCentre + bounds.height() / 2f, numberPaint)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.02f
        }
        grow(namePaint, name, SIZE * NAME_MAX_WIDTH, SIZE * (1f - NUMBER_BAND_TOP - NUMBER_BAND_HEIGHT))
        canvas.drawText(name, SIZE / 2f, SIZE * NAME_BASELINE - namePaint.descent(), namePaint)

        return bitmap
    }

    /** Raise the text size until it hits the width or the height of its band. */
    private fun grow(paint: Paint, text: String, maxWidth: Float, maxHeight: Float) {
        if (text.isEmpty()) return
        var size = 4f
        while (size < 200f) {
            paint.textSize = size + 1f
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            if (paint.measureText(text) > maxWidth || bounds.height() > maxHeight) break
            size += 1f
        }
        paint.textSize = size
    }
}
