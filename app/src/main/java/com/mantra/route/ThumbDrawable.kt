package com.mantra.route

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * The slider thumb, with the percentage written inside it.
 *
 * A number printed beside the slider makes the eye travel: the thumb is where the finger and
 * the attention already are, so the number belongs there. It also removes the row of "12 / 16"
 * step counts entirely — those were the platform's units, not anyone's, and 12 of 16 means
 * nothing without doing the division.
 *
 * Written as a Drawable rather than a View because a SeekBar's thumb is a Drawable; wrapping a
 * TextView would have meant reimplementing the SeekBar.
 */
class ThumbDrawable(
    private val sizePx: Int,
    fillColour: Int,
    textColour: Int,
) : Drawable() {

    /** The text inside the circle. Set on every progress change. */
    var label: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColour
        style = Paint.Style.FILL
    }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColour
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val bounds = Rect()

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val r = getBounds()
        val cx = r.exactCenterX()
        val cy = r.exactCenterY()
        val radius = minOf(r.width(), r.height()) / 2f
        canvas.drawCircle(cx, cy, radius, circle)
        if (label.isEmpty()) return

        // Grow until the text touches the circle, testing the CORNER of its box: a line through
        // the middle is widest there, but its top and bottom corners are where the curve has
        // already closed in.
        var size = 4f
        while (size < radius * 2f) {
            text.textSize = size + 1f
            text.getTextBounds(label, 0, label.length, bounds)
            val halfW = bounds.width() / 2f
            val halfH = bounds.height() / 2f
            if (kotlin.math.hypot(halfW, halfH) > radius * 0.80f) break
            size += 1f
        }
        text.textSize = size
        text.getTextBounds(label, 0, label.length, bounds)
        // Centre on the ink, not the font's line box.
        canvas.drawText(label, cx, cy + bounds.height() / 2f, text)
    }

    override fun setAlpha(alpha: Int) {
        circle.alpha = alpha
        text.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        circle.colorFilter = colorFilter
    }

    @Deprecated("Required by Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
