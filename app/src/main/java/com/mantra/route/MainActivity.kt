package com.mantra.route

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The app screen, after Shizuku.
 *
 * v18 removed the probe, the patch bay, the capability list, mono, balance and the call
 * release. All of them needed a privileged shell started over adb, which needs Wireless
 * debugging, which needs Wi-Fi. Baba is often without it, so those features were not merely
 * inconvenient, they were unreachable — and an unreachable feature is not a feature.
 *
 * What is left is the sliders, one Settings shortcut, and a report to paste. The real interface
 * is Quick Settings now.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var router: Router
    private lateinit var volumeRows: LinearLayout

    /** One row per stream, built once and then refreshed in place. */
    private val rows = mutableListOf<Triple<Stream, TextView, SeekBar>>()

    /**
     * Volume can change from anywhere — the tiles, the hardware keys, the system panel — and
     * none of those tell this screen. Reading only in onResume meant a change made in Quick
     * Settings never reached an open app at all, because pulling the shade down does not pause
     * the activity underneath it.
     *
     * The settings table is the one place every one of those routes writes to.
     */
    private val volumeWatcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshVolumes()
    }
    private lateinit var dndButton: TextView
    private lateinit var copyButton: TextView
    private lateinit var bannerButton: TextView




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        router = Router(this)

        // Android 15 made edge-to-edge mandatory for targetSdk 35+; a layout that does not
        // consume the insets draws behind the clock and behind the navigation buttons.
        val root = findViewById<View>(R.id.root_scroll)
        val basePadding = root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top + basePadding, bars.right, bars.bottom + basePadding)
            insets
        }

        volumeRows = findViewById(R.id.volume_rows)
        dndButton = findViewById(R.id.dnd_button)
        copyButton = findViewById(R.id.copy_button)
        bannerButton = findViewById(R.id.banner_button)
        findViewById<TextView>(R.id.version).text = "v" + BuildConfig.VERSION_NAME




        buildVolumeRows()

        dndButton.setOnClickListener {
            press(dndButton, "Do Not Disturb access") {
                val manager = getSystemService(NotificationManager::class.java)
                if (manager.isNotificationPolicyAccessGranted) {
                    "Already granted — Ring and Notification work"
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

        bannerButton.setOnClickListener {
            press(bannerButton, "Top banner") {
                if (StatusBanner.canShow(this)) {
                    StatusBanner.show(this, "MANTRA ROUTE  READY")
                    "Already allowed — that line is the banner"
                } else {
                    startActivity(StatusBanner.overlaySettings(this))
                    "Allow Mantra Route to display over other apps"
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
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, volumeWatcher,
        )
        refreshVolumes()
    }

    override fun onPause() {
        super.onPause()
        runCatching { contentResolver.unregisterContentObserver(volumeWatcher) }
    }






    /**
     * The press rule, the same for every control.
     *
     * Three channels at once because any one can be missed: the label becomes the RESULT and
     * holds long enough to read, the button turns amber, and a haptic tick fires — the only
     * channel that works when you are not looking at the screen.
     */
    private fun press(button: TextView, restingLabel: String, run: () -> String) {
        button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        // The work is wrapped, and this is the reason: every button's action reaches a system
        // service that can refuse. The clipboard throws on some OEM builds, a Settings screen
        // can be absent, an audio effect can be unavailable. Unwrapped, any of those took the
        // whole app down AND left the button frozen on its old label — the crash and the lie in
        // one step. Now the fault becomes the button's text, which is where a fault belongs.
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

    /** Build the rows once. Their values are filled in by refreshVolumes(). */
    private fun buildVolumeRows() {
        volumeRows.removeAllViews()
        rows.clear()
        Volume.STREAMS.forEach { stream ->
            val view = layoutInflater.inflate(R.layout.volume_row, volumeRows, false)
            val label = view.findViewById<TextView>(R.id.volume_label)
            val bar = view.findViewById<SeekBar>(R.id.volume_bar)

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                    // Guarded: refreshVolumes() sets progress itself, and without this every
                    // observer callback would redraw the label from a half-applied value.
                    if (!fromUser) return
                    val max = router.volumeMax(stream.id)
                    label.text = Volume.label(stream.label, Volume.indexFor(p, max), max)
                }

                override fun onStartTrackingTouch(b: SeekBar) = Unit

                /** Applied on release: each apply is a real system call. */
                override fun onStopTrackingTouch(b: SeekBar) {
                    b.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val max = router.volumeMax(stream.id)
                    val outcome = router.setVolume(stream.id, Volume.indexFor(b.progress, max))
                    if (outcome is Router.Outcome.Refused) {
                        label.text = outcome.why
                        label.setTextColor(color(R.color.fault))
                    }
                    refreshVolumes()
                    val now = router.volumeIndex(stream.id)
                    StatusBanner.show(
                        this@MainActivity,
                        TileText.banner(stream.label, Volume.percentFor(now, max), max),
                    )
                    // The tiles cannot see this happen. Tell them.
                    TileNudge.all(this@MainActivity)
                }
            })
            volumeRows.addView(view)
            rows.add(Triple(stream, label, bar))
        }
    }

    /** Re-read every stream from the platform and put the numbers on screen. */
    private fun refreshVolumes() {
        rows.forEach { (stream, label, bar) ->
            val max = router.volumeMax(stream.id)
            val index = router.volumeIndex(stream.id)
            label.text = Volume.label(stream.label, index, max)
            label.setTextColor(color(if (Volume.isLow(index, max)) R.color.fault else R.color.sand))
            bar.isEnabled = max > 0
            bar.progress = Volume.percentFor(index, max)
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)


    /** The screen as plain text, for pasting. */
    private fun report(): String = buildString {
        append("Mantra Route v").append(BuildConfig.VERSION_NAME).append('\n')
        append("Android ").append(Build.VERSION.SDK_INT)
            .append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        val granted = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        append("Do Not Disturb access: ").append(if (granted) "granted" else "not granted").append("\n\n")
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
