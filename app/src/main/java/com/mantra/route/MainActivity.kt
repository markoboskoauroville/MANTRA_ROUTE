package com.mantra.route

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var dndButton: TextView
    private lateinit var copyButton: TextView
    private lateinit var vu: VuView
    private lateinit var vuLine: TextView
    private lateinit var normalizeButton: TextView
    private lateinit var boostCheck: CheckBox

    private val meter = OutputMeter()

    /** Gain currently applied, so the checkbox can put it back without measuring again. */
    private var lastGainMb = 0

    private val askMicrophone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startMeter() else vuLine.text =
                "the meter needs the microphone permission to read the output mix"
        }

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
        vu = findViewById(R.id.vu)
        vuLine = findViewById(R.id.vu_line)
        normalizeButton = findViewById(R.id.normalize_button)
        boostCheck = findViewById(R.id.boost_check)
        findViewById<TextView>(R.id.version).text = "v" + BuildConfig.VERSION_NAME

        vu.source = { meter.peakDb }

        normalizeButton.setOnClickListener {
            press(normalizeButton, "Normalize") { normalize() }
        }

        // The checkbox does not measure. It puts back, or takes away, the gain normalise found.
        // Unticking it must be instant and total, because the reason to untick it is that the
        // room just got too loud.
        boostCheck.setOnCheckedChangeListener { _, checked ->
            if (checked && lastGainMb <= 0) {
                boostCheck.isChecked = false
                vuLine.text = "press Normalize first — there is no measured gain to apply"
            } else {
                val failure = meter.setGainMb(if (checked) lastGainMb else 0)
                vuLine.text = when {
                    failure != null -> failure
                    checked -> "boost on, " + Normalize.describe(lastGainMb)
                    else -> "boost off"
                }
            }
        }

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
        drawVolumes()
        // If the screen was left mid-measurement the watcher may never have run again, and the
        // button would stay disabled for the life of the process. Re-enabling on every resume
        // costs nothing and closes that.
        normalizeButton.isEnabled = true
        if (hasMicrophone()) startMeter() else {
            vuLine.text = "tap Normalize to let the meter read the output mix"
        }
    }

    /**
     * The Visualizer holds a capture of the global mix. Letting it run while the screen is not
     * in front of anyone keeps that capture open for no reason, and Android may refuse it in the
     * background anyway.
     */
    override fun onPause() {
        super.onPause()
        meter.release()
    }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startMeter() {
        if (meter.start()) {
            vuLine.text = "reading the output mix"
        } else {
            // A dead meter and a silent room look identical, so the reason goes on the screen.
            vuLine.text = meter.lastError ?: "this build refuses to meter the output mix"
        }
    }

    /**
     * Measure the loudest moment over a short window, then make up the difference.
     *
     * A single instant is not a peak — it is whatever happened in one 40ms frame, which on a
     * quiet passage is silence and would ask for twenty decibels of gain. So it watches for a
     * while and keeps the highest.
     */
    private fun normalize(): String {
        if (!hasMicrophone()) {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            return "asking for the microphone, for the meter"
        }
        if (!meter.isMetering && !meter.start()) {
            return meter.lastError ?: "cannot read the output mix on this build"
        }

        normalizeButton.isEnabled = false
        var highest = Meter.FLOOR_DB
        val started = System.currentTimeMillis()
        val watcher = object : Runnable {
            override fun run() {
                highest = maxOf(highest, meter.peakDb)
                if (System.currentTimeMillis() - started < WATCH_MS) {
                    vu.postDelayed(this, 40L)
                } else {
                    normalizeButton.isEnabled = true
                    finishNormalize(highest)
                }
            }
        }
        vu.post(watcher)
        return "listening…"
    }

    private fun finishNormalize(peakDb: Float) {
        if (!Normalize.hasSignal(peakDb)) {
            vuLine.text = "nothing playing to measure — start the audio, then Normalize"
            return
        }
        lastGainMb = Normalize.gainMb(peakDb)
        if (lastGainMb <= 0) {
            boostCheck.isChecked = false
            vuLine.text = "peak " + String.format("%.1f", peakDb) + " dB — already at full level"
            return
        }
        val failure = meter.setGainMb(lastGainMb)
        if (failure != null) {
            boostCheck.isChecked = false
            vuLine.text = failure
            return
        }
        // Ticked because a gain was found AND applied. The checkbox reports a fact, it does not
        // announce an intention.
        boostCheck.isChecked = true
        vuLine.text = "peak " + String.format("%.1f", peakDb) + " dB — " + Normalize.describe(lastGainMb)
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

    private fun drawVolumes() {
        volumeRows.removeAllViews()
        Volume.STREAMS.forEach { stream ->
            val view = layoutInflater.inflate(R.layout.volume_row, volumeRows, false)
            val label = view.findViewById<TextView>(R.id.volume_label)
            val bar = view.findViewById<SeekBar>(R.id.volume_bar)

            val max = router.volumeMax(stream.id)
            val index = router.volumeIndex(stream.id)
            label.text = Volume.label(stream.label, index, max)
            label.setTextColor(color(if (Volume.isLow(index, max)) R.color.fault else R.color.sand))
            bar.progress = Volume.percentFor(index, max)
            bar.isEnabled = max > 0

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) label.text = Volume.label(stream.label, Volume.indexFor(p, max), max)
                }

                override fun onStartTrackingTouch(b: SeekBar) = Unit

                /** Applied on release, not on every pixel: each apply is a real system call. */
                override fun onStopTrackingTouch(b: SeekBar) {
                    b.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val outcome = router.setVolume(stream.id, Volume.indexFor(b.progress, max))
                    val now = router.volumeIndex(stream.id)
                    label.text = when (outcome) {
                        is Router.Outcome.Moved -> Volume.label(stream.label, now, max)
                        is Router.Outcome.Refused -> outcome.why
                    }
                    label.setTextColor(
                        color(
                            when {
                                outcome is Router.Outcome.Refused -> R.color.fault
                                Volume.isLow(now, max) -> R.color.fault
                                else -> R.color.amber
                            }
                        )
                    )
                    b.progress = Volume.percentFor(now, max)
                }
            })
            volumeRows.addView(view)
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private companion object {
        /** Long enough for a loud moment to arrive, short enough not to feel stuck. */
        const val WATCH_MS = 2500L
    }

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
