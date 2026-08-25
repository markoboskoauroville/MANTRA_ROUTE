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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Everything that is not a slider.
 *
 * The three permission and diagnostic buttons used to sit above the sliders on the main screen,
 * where they were passed over every time and cost a third of the height permanently. They are
 * used once each, so they live one tap away instead.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var bannerButton: TextView
    private lateinit var bannerState: TextView
    private lateinit var dndButton: TextView
    private lateinit var dndState: TextView
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
            view.setPadding(bars.left, bars.top + basePadding, bars.right, bars.bottom + basePadding)
            insets
        }

        bannerButton = findViewById(R.id.banner_button)
        bannerState = findViewById(R.id.banner_state)
        dndButton = findViewById(R.id.dnd_button)
        dndState = findViewById(R.id.dnd_state)
        copyButton = findViewById(R.id.copy_button)
        findViewById<TextView>(R.id.version).text = "v" + BuildConfig.VERSION_NAME

        bannerButton.setOnClickListener {
            press(bannerButton, "Top banner") {
                if (StatusBanner.canShow(this)) {
                    StatusBanner.show(this, "MANTRA ROUTE  READY")
                    "That line at the top is the banner"
                } else {
                    startActivity(StatusBanner.overlaySettings(this))
                    "Switch Mantra Route on in the list"
                }
            }
        }

        dndButton.setOnClickListener {
            press(dndButton, "Do Not Disturb access") {
                val manager = getSystemService(NotificationManager::class.java)
                if (manager.isNotificationPolicyAccessGranted) {
                    "Already granted"
                } else {
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Find Mantra Route and switch it on"
                    }.getOrElse { "this build has no Do Not Disturb access screen" }
                }
            }
        }

        copyButton.setOnClickListener {
            press(copyButton, "Copy the report") {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Mantra Route report", report()))
                "Copied — paste it anywhere"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Read on every resume, because both of these are granted by leaving this screen and
        // coming back. A state read once at launch would be stale exactly when it mattered.
        val overlay = StatusBanner.canShow(this)
        bannerState.text =
            if (overlay) "Allowed — a tile press shows a line across the top"
            else "Not allowed — a tile press falls back to a small message at the bottom"
        bannerState.setTextColor(color(if (overlay) R.color.amber else R.color.slate_ink))

        val dnd = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        dndState.text =
            if (dnd) "Allowed — Ring and Notification work under Do Not Disturb"
            else "Not allowed — Ring and Notification are blocked while Do Not Disturb is on"
        dndState.setTextColor(color(if (dnd) R.color.amber else R.color.slate_ink))
    }

    /** Label becomes the result, colour changes, haptic tick. Any fault becomes the label. */
    private fun press(button: TextView, restingLabel: String, run: () -> String) {
        button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val result = runCatching { run() }.getOrElse { t ->
            (t.cause ?: t).javaClass.simpleName + ": " + (t.message ?: "refused")
        }
        button.text = Feedback.resultLabel(result)
        button.setTextColor(color(R.color.amber))
        button.postDelayed({
            button.text = restingLabel
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
            append("Top banner: ").append(if (StatusBanner.canShow(this@SettingsActivity)) "allowed" else "not allowed").append('\n')
            val dnd = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
            append("Do Not Disturb access: ").append(if (dnd) "granted" else "not granted").append("\n\n")
            append("VOLUME\n")
            Volume.STREAMS.forEach { stream ->
                val max = router.volumeMax(stream.id)
                val index = router.volumeIndex(stream.id)
                append("  ").append(Volume.label(stream.label, index, max))
                if (Volume.isLow(index, max)) append("   <-- LOW")
                append('\n')
            }
        }
    }
}
