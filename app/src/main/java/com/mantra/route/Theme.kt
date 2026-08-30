package com.mantra.route

import android.content.Context
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity

/**
 * The chosen scheme, and the drawables built from it.
 *
 * The track and the button backgrounds used to be XML drawables with the colours written into
 * them, which cannot follow a scheme — an XML colour is fixed at build time. They are built
 * here instead, from whichever scheme is selected.
 */
object Theme {

    private const val PREFS = "mantra_route_theme"
    private const val KEY = "scheme"

    fun current(context: Context): Scheme =
        Schemes.byIndex(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
        )

    fun currentIndex(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY, 0).coerceIn(0, Schemes.ALL.lastIndex)

    fun choose(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, index.coerceIn(0, Schemes.ALL.lastIndex)).apply()
    }

    fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics,
    ).toInt()

    /** The slider track: an empty bar with a filled portion clipped over it. */
    fun track(context: Context, scheme: Scheme): LayerDrawable {
        val radius = dp(context, 8f).toFloat()
        val height = dp(context, 16f)

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(scheme.surface)
            setSize(0, height)
        }
        val filled = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(scheme.progress)
            setSize(0, height)
        }
        val progress = ClipDrawable(filled, Gravity.START, ClipDrawable.HORIZONTAL)

        return LayerDrawable(arrayOf(background, progress)).apply {
            // The ids matter: a SeekBar looks the progress layer up by id, and without them it
            // draws the empty bar and never fills.
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    /** A button, or the key-preview bubble. */
    fun rounded(context: Context, colour: Int, radiusDp: Float = 4f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(colour)
        }

    fun circle(colour: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colour)
    }
}
