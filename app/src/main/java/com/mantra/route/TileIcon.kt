package com.mantra.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * The face of a Quick Settings tile: one line, as large as the circle allows.
 *
 * Two letters and a digit — "CA2" — and nothing else. v20 stacked three rows and each of them
 * was necessarily a third of the height; one row gets the whole of it, which is roughly three
 * times the ink for the same square.
 *
 * A tile is a CIRCLE, and a single centred line sits on its widest chord, so this one row may
 * run the full diameter. That is the whole reason one line beats three: the rows above and
 * below the middle were paying for the curve.
 */
object TileIcon {

    private const val SIZE = 192f

    /** A hair inside the radius so antialiasing at the extreme edge is not clipped. */
    private const val INSET = 0.97f

    fun render(face: String): Bitmap {
        val size = SIZE.toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (face.isEmpty()) return bitmap

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        grow(paint, face)

        // Centre on the INK, not the font's line box: the box carries ascender and descender
        // room that capitals and digits never use, so centring on it sits visibly high.
        val bounds = Rect()
        paint.getTextBounds(face, 0, face.length, bounds)
        canvas.drawText(face, SIZE / 2f, SIZE / 2f + bounds.height() / 2f, paint)
        return bitmap
    }

    /**
     * Raise the size until the text box's CORNER touches the circle.
     *
     * Not width and height separately, which is what the first attempt did and why "CA2" came
     * out clipped at the sides: a centred line sits on the diameter only at its MIDLINE, and its
     * top and bottom corners are out where the circle has already closed in. Capping width at
     * 96% of the square and height at 58% put those corners well outside the glass.
     *
     * The corner is the only point that matters, so the test is the one Pythagoras gives:
     * half-width and half-height are the legs, the radius is the hypotenuse it must not exceed.
     * That finds the largest text the circle can actually hold, which for a three-character
     * face is about 80px of a 192px square.
     */
    private fun grow(paint: Paint, text: String) {
        val radius = SIZE / 2f * INSET
        var size = 4f
        val bounds = Rect()
        while (size < 260f) {
            paint.textSize = size + 1f
            paint.getTextBounds(text, 0, text.length, bounds)
            val halfW = bounds.width() / 2f
            val halfH = bounds.height() / 2f
            if (kotlin.math.sqrt(halfW * halfW + halfH * halfH) > radius) break
            size += 1f
        }
        paint.textSize = size
    }
}
