package com.mantra.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * The face of a Quick Settings tile: two letters and a digit, one line.
 *
 * ONE SIZE FOR EVERY FACE. v21 grew each face independently until it hit the edge, and the
 * result was that `RI1` came out at 93px while `ME1` came out at 74 — a quarter larger, because
 * "I" is a narrow glyph so the string is shorter and had further to grow. Five tiles in a row,
 * one visibly fatter than the rest, and nothing about the volume to explain it.
 *
 * Reported as "RI is more fat than the others". It was, by 26%.
 *
 * So the size is computed ONCE across every face the app can ever draw, and the faces that are
 * naturally too wide at that size are CONDENSED horizontally rather than shrunk. Condensing
 * keeps the cap height and the stroke weight identical, which is what the eye reads as "the
 * same size"; shrinking would have made them match by making them all as small as the widest.
 * The squeeze is held to 10%, below which it does not read as distortion.
 */
object TileIcon {

    private const val SIZE = 192f
    private const val MAX_W = SIZE * 0.94f
    private const val MAX_H = SIZE * 0.62f

    /** Below this the letterforms start to look wrong rather than merely narrow. */
    private const val MIN_SCALE_X = 0.90f

    private val cache = HashMap<String, Bitmap>()

    /** Every face this app can draw: five channels by four levels. */
    private val allFaces: List<String> by lazy {
        Volume.STREAMS.flatMap { s -> Presets.LEVELS.map { TileText.two(s.label) + Presets.digit(it) } }
    }

    /**
     * The one size, found by growing until the WIDEST face would need more than a 10% squeeze,
     * or the tallest would leave its band.
     */
    private val uniformSize: Float by lazy {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
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

        val size = SIZE.toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (face.isEmpty()) return bitmap

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = uniformSize
        }
        // Condense only as much as this particular face needs. Narrow faces are untouched.
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
}
