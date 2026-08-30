package com.mantra.route

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Three buttons, a way back, and the version. No prose.
 *
 * The captions went because they were read once and then occupied the screen for ever. The
 * separate "Allowed / Not allowed" line went too — but the state it carried did NOT: it is
 * folded into the button's own label, which is where §5 says it belongs anyway.
 *
 * The Top banner button went with the overlay in v33: an overlay cannot draw over the
 * notification shade, so it could never do the one job it was added for.
 *
 * Laid out like the sliders: each control takes an equal share of the height, so the whole
 * screen is a target instead of a list with dead space beneath it.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var dndButton: TextView
    private lateinit var copyButton: TextView
    private lateinit var scheme: Scheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        scheme = Theme.current(this)

        val root = findViewById<View>(R.id.settings_root)
        val basePadding = root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                view.paddingLeft, bars.top + basePadding, view.paddingRight, bars.bottom + basePadding
            )
            insets
        }

        dndButton = findViewById(R.id.dnd_button)
        copyButton = findViewById(R.id.copy_button)
        findViewById<TextView>(R.id.version).text = "v" + BuildConfig.VERSION_NAME
        buildSwatches()
        paintScheme()

        // There was no way out of this screen except the system back gesture, which is not an
        // affordance — nothing on screen said it existed.
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
        }


        dndButton.setOnClickListener {
            press(dndButton, ::dndLabel) {
                if (getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
                    "Already granted"
                } else {
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Find Mantra Route and switch it on"
                    }.getOrElse { "no Do Not Disturb screen on this build" }
                }
            }
        }

        copyButton.setOnClickListener {
            press(copyButton, { "Copy the report" }) {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Mantra Route report", report()))
                "Copied — paste it anywhere"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Read on every resume: both are granted by leaving this screen and coming back, so a
        // value read once at launch would be stale exactly when it mattered.
        dndButton.text = dndLabel()
        dndButton.setTextColor(if (dndGranted()) scheme.accent else scheme.ink)
    }

    private fun dndGranted() =
        getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted


    private fun dndLabel() =
        "Do Not Disturb access\n" + if (dndGranted()) "allowed" else "not allowed"

    /** Label becomes the result, colour changes, haptic tick. Any fault becomes the label. */
    private fun press(button: TextView, resting: () -> String, run: () -> String) {
        button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val result = runCatching { run() }.getOrElse { t ->
            (t.cause ?: t).javaClass.simpleName + ": " + (t.message ?: "refused")
        }
        button.text = Feedback.resultLabel(result)
        button.setTextColor(scheme.accent)
        button.postDelayed({
            button.text = resting()
            button.setTextColor(scheme.ink)
        }, Feedback.HOLD_MS)
    }

    /**
     * Ten swatches, five to a row.
     *
     * Each one is painted in its OWN scheme rather than the current one — a row of swatches
     * that all share the selected palette tells you nothing about what you are choosing. The
     * selected swatch is marked by a ring in its own ink, so the mark is legible whichever
     * scheme is showing.
     */
    private fun buildSwatches() {
        val rows = listOf<LinearLayout>(findViewById(R.id.swatches_a), findViewById(R.id.swatches_b))
        rows.forEach { it.removeAllViews() }
        Schemes.ALL.forEachIndexed { index, s ->
            val row = rows[index / 5]
            val swatch = TextView(this)
            swatch.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                .apply { marginStart = if (index % 5 == 0) 0 else Theme.dp(this@SettingsActivity, 8f) }
            swatch.gravity = android.view.Gravity.CENTER
            swatch.text = s.name.take(2)
            swatch.textSize = 13f
            swatch.setTextColor(s.ink)
            swatch.contentDescription = s.name
            swatch.background = Theme.rounded(this, s.ground, 10f).apply {
                setStroke(
                    Theme.dp(this@SettingsActivity, if (index == Theme.currentIndex(this@SettingsActivity)) 4f else 1f),
                    if (index == Theme.currentIndex(this@SettingsActivity)) s.accent else s.surface,
                )
            }
            swatch.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                Theme.choose(this, index)
                scheme = Theme.current(this)
                buildSwatches()
                paintScheme()
            }
            row.addView(swatch)
        }
    }

    /** Repaint this screen in the chosen scheme, without recreating the activity. */
    private fun paintScheme() {
        findViewById<View>(R.id.settings_root).setBackgroundColor(scheme.ground)
        findViewById<ImageView>(R.id.back_button).setColorFilter(scheme.ink)
        findViewById<TextView>(R.id.version).setTextColor(scheme.muted)
        listOf(dndButton, copyButton).forEach {
            it.background = Theme.rounded(this, scheme.surface)
            it.setTextColor(scheme.ink)
        }
    }

    private fun report(): String {
        val router = Router(this)
        return buildString {
            append("Mantra Route v").append(BuildConfig.VERSION_NAME).append('\n')
            append("Android ").append(Build.VERSION.SDK_INT)
                .append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Do Not Disturb access: ").append(if (dndGranted()) "granted" else "not granted").append("\n\n")
            append("VOLUME\n")
            Volume.STREAMS.forEach { stream ->
                val max = router.volumeMax(stream.id)
                val index = router.volumeIndex(stream.id)
                // The report keeps the raw steps as well as the percentage: it is a
                // diagnostic, and "12 of 16" is exactly the detail that explains why a stream
                // cannot land on 25 when another can.
                append("  ").append(stream.label)
                    .append("  ").append(Volume.percentFor(index, max)).append('%')
                    .append("  (").append(index).append('/').append(max).append(')')
                if (Volume.isLow(index, max)) append("   <-- LOW")
                append('\n')
            }
        }
    }
}
