package com.mantra.route

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * The meter, drawn exactly as TTT Mini draws it.
 *
 * A bar alone shows the current instant, which at music rates is a flicker. The peak mark is
 * what makes it a meter rather than a light.
 */
class VuView(context: Context) : View(context) {

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Meter.TRACK }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Meter.PEAK_INK }

    private var smoothed = Meter.FLOOR_DB
    private var peakDb = Meter.FLOOR_DB

    /** Where the level comes from. Set by the screen; null means nothing is metering. */
    var source: (() -> Float)? = null

    private val tick = object : Runnable {
        override fun run() {
            val db = source?.invoke() ?: Meter.FLOOR_DB
            smoothed = Meter.smooth(smoothed, db)
            peakDb = Meter.decayPeak(peakDb, db)
            invalidate()
            postDelayed(this, 40L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        canvas.drawRoundRect(0f, 0f, w, h, r, r, track)

        val n = Meter.norm(smoothed)
        if (n > 0f) {
            bar.color = Meter.colourFor(smoothed)
            canvas.drawRoundRect(0f, 0f, w * n, h, r, r, bar)
        }
        val p = Meter.norm(peakDb)
        if (p > 0f) {
            val x = (w * p).coerceIn(2f, w - 2f)
            canvas.drawRect(x - 1.5f, 0f, x + 1.5f, h, peakPaint)
        }
    }
}
