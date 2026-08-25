package com.mantra.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * The face of a Quick Settings tile: the channel letter and the level, on ONE line.
 *
 * v28 split this onto two lines because width was the binding constraint and the letter could
 * then be drawn much larger. It measured better and looked worse — a big letter above a small
 * number reads as two things stacked rather than one label, and the number, which is what is
 * actually being read, ended up the smaller of the two. Reverted on that basis.
 *
 * ONE SIZE FOR EVERY FACE. Growing each face independently made `R44` a quarter larger than
 * `M100`, because narrow glyphs leave a shorter string with further to grow. The size is
 * computed once across every face the app can draw, and faces too wide at that size are
 * CONDENSED rather than shrunk: condensing keeps the cap height and stroke weight identical,
 * which is what the eye reads as "the same size". The squeeze is held to 10%, below which it
 * does not register as distortion.
 */
object TileIcon {

    private const val SIZE = 192f
    private const val MAX_W = SIZE * 0.94f
    private const val MAX_H = SIZE * 0.62f

    /** Below this the letterforms look wrong rather than merely narrow. */
    private const val MIN_SCALE_X = 0.90f

    private val cache = HashMap<String, Bitmap>()

    /**
     * The widest face each channel can produce: its letter plus "100". Sizing against the four
     * presets alone would leave every tile too large the moment the slider lands on a
     * three-digit number.
     */
    private val allFaces: List<String> by lazy {
        Volume.STREAMS.map { TileText.one(it.label) + "100" }
    }

    private val uniformSize: Float by lazy {
        val paint = paint()
        val bounds = Rect()
        var size = 4f
        while (size < 260f) {
            paint.textSize = size + 1f
            var widest = 0f
            var tallest = 0
            allFaces.forEach {
                widest = maxOf(widest, paint.measureText(it))
                paint.getTextBounds(it, 0, it.length, bounds)
                tallest = maxOf(tallest, bounds.height())
            }
            if (tallest > MAX_H || MAX_W / widest < MIN_SCALE_X) break
            size += 1f
        }
        size
    }

    fun render(face: String): Bitmap {
        cache[face]?.let { if (!it.isRecycled) return it }

        val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (face.isEmpty()) return bitmap

        val paint = paint().apply { textSize = uniformSize }
        // Condense only as much as this particular face needs; narrow faces are untouched.
        val natural = paint.measureText(face)
        if (natural > MAX_W) paint.textScaleX = MAX_W / natural

        // Centre on the INK, not the font's line box: the box carries ascender and descender
        // room that capitals and digits never use, so centring on it sits visibly high.
        val bounds = Rect()
        paint.getTextBounds(face, 0, face.length, bounds)
        canvas.drawText(face, SIZE / 2f, SIZE / 2f + bounds.height() / 2f, paint)

        cache[face] = bitmap
        return bitmap
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
}
