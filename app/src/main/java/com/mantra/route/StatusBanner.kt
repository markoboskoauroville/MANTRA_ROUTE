package com.mantra.route

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * A wide line across the top of the screen saying what just changed.
 *
 * A toast cannot do this. Since Android 11 a text toast ignores `setGravity` entirely, and
 * custom-view toasts are blocked from the background — which is where a Quick Settings tile
 * runs. So a toast appears wherever the platform decides, small, at the bottom. That is why
 * this is an overlay window instead: it is the only way to put a large line at the TOP of the
 * screen from a tile press.
 *
 * The cost is the "Display over other apps" permission. It is granted on the phone in Settings,
 * needs no computer, and if it is not granted the tile falls back to the toast rather than
 * failing — a smaller message in the wrong place still beats no message.
 */
object StatusBanner {

    /** Long enough to read a short line, short enough not to sit over what you are doing. */
    private const val SHOW_MS = 1400L

    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var hide: Runnable? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettings(context: Context) = android.content.Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        android.net.Uri.parse("package:" + context.packageName),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Show [line] across the top. Safe to call repeatedly: a second press replaces the first
     * rather than stacking, and restarts the clock.
     */
    fun show(context: Context, line: String) {
        if (!canShow(context)) return
        main.post {
            runCatching {
                val windows = context.getSystemService(WindowManager::class.java)
                    ?: return@runCatching

                // Reuse the view. Adding a second window per press would leak one per tap and
                // leave the screen stacked with stale lines.
                val text = view ?: TextView(context.applicationContext).also { view = it }
                text.text = line
                text.setTextColor(Color.WHITE)
                text.setBackgroundColor(0xE6000000.toInt())
                text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                text.gravity = Gravity.CENTER
                text.setPadding(24, 28, 24, 28)
                text.maxLines = 1
                // As large as the width allows, found by the platform rather than guessed.
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    text, 18, 64, 2, TypedValue.COMPLEX_UNIT_SP,
                )

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // NOT_TOUCHABLE matters: without it this line would swallow taps meant for
                    // whatever is underneath, including the tile that put it there.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply { gravity = Gravity.TOP }

                if (text.parent == null) windows.addView(text, params)
                else windows.updateViewLayout(text, params)

                hide?.let { main.removeCallbacks(it) }
                val next = Runnable { dismiss(context) }
                hide = next
                main.postDelayed(next, SHOW_MS)
            }
        }
    }

    fun dismiss(context: Context) {
        runCatching {
            val windows = context.getSystemService(WindowManager::class.java)
            view?.let { if (it.parent != null) windows?.removeView(it) }
        }
        view = null
        hide = null
    }
}
