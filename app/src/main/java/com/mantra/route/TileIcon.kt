package com.mantra.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat

/**
 * A tile icon with the level written into it.
 *
 * Quick Settings gives a tile a single small square and, on this phone, nothing else — no label
 * and no subtitle are drawn in the collapsed grid. So the only way to put a number where it can
 * be seen is to draw it into the icon.
 *
 * Rendered large and left to scale down: the platform decides the final size and a bitmap built
 * at tile resolution would be soft on a 3x screen.
 */
object TileIcon {

    private const val SIZE = 192
    private const val GLYPH_FRACTION = 0.60f

    fun render(context: Context, glyphRes: Int, badge: String): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // The glyph, in the top 60%, centred.
        val glyphBox = (SIZE * GLYPH_FRACTION).toInt()
        ContextCompat.getDrawable(context, glyphRes)?.let { drawable ->
            val left = (SIZE - glyphBox) / 2
            drawable.setTint(Color.WHITE)
            drawable.setBounds(left, 0, left + glyphBox, glyphBox)
            drawable.draw(canvas)
        }

        // The number, in the remaining 40%. Bold, because it is competing with a glyph for
        // attention at a size where thin strokes disappear.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = SIZE * 0.30f
        }
        // Shrink to fit rather than clip: "100%" is wider than "50%" and must not be cut.
        while (paint.measureText(badge) > SIZE * 0.95f && paint.textSize > 8f) {
            paint.textSize -= 2f
        }
        canvas.drawText(badge, SIZE / 2f, SIZE * 0.97f, paint)

        return bitmap
    }
}
