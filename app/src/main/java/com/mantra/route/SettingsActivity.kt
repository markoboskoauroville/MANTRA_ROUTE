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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
        dndButton.setTextColor(color(if (dndGranted()) R.color.amber else R.color.sand))
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
        button.setTextColor(color(R.color.amber))
        button.postDelayed({
            button.text = resting()
            button.setTextColor(color(R.color.sand))
        }, Feedback.HOLD_MS)
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

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
