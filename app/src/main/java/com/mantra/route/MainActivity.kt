package com.mantra.route

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * The screen.
 *
 * design-language.md §1 governs the whole thing: every control is rendered on the first frame
 * and dimmed when it cannot be used. Nothing is inflated when a probe comes back green and
 * nothing is torn out when it comes back red — the row is already there, it changes colour.
 * The list of probes therefore has a fixed height from launch, and pressing Probe does not
 * make the page grow under the thumb.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var state: State
    private lateinit var router: Router
    private lateinit var watcher: OutputWatcher

    private lateinit var shizukuLine: TextView
    private lateinit var probeButton: TextView
    private lateinit var probeRows: LinearLayout
    private lateinit var balanceBar: SeekBar
    private lateinit var balanceLine: TextView
    private lateinit var notifButton: TextView

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { redraw() }

    /**
     * Without BLUETOOTH_CONNECT the platform still lists Bluetooth outputs, but hands back an
     * empty product name and an empty address. That is worse than not listing them: two rows
     * called "Bluetooth" that cannot be told apart, and a dedup key with nothing in it. So it
     * is asked for at launch rather than at the moment a headset connects.
     */
    private val askBluetooth =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { redraw() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        state = State(this)
        router = Router(this)
        watcher = OutputWatcher(this)

        shizukuLine = findViewById(R.id.shizuku_line)
        probeButton = findViewById(R.id.probe_button)
        probeRows = findViewById(R.id.probe_rows)
        balanceBar = findViewById(R.id.balance_bar)
        balanceLine = findViewById(R.id.balance_line)
        notifButton = findViewById(R.id.notif_button)

        findViewById<TextView>(R.id.version).text = "v" + BuildConfig.VERSION_NAME

        // The rows exist from the first frame, before anything has been probed, holding
        // UNTESTED. A screen that fills in is a screen that jumped.
        buildProbeRows(Probe.runAllTitlesOnly())

        probeButton.setOnClickListener { probe() }

        notifButton.setOnClickListener {
            if (needsNotificationPermission()) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Notifier.post(this)
                redraw()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        balanceBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                balanceLine.text = balanceLabel(progressToBalance(progress))
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            /**
             * §11: a dial is judged against what it is doing. Applied on release rather than on
             * every pixel of the drag, because each apply is two shell round trips and firing
             * them continuously makes the slider feel like it is fighting back.
             */
            override fun onStopTrackingTouch(bar: SeekBar) {
                val value = progressToBalance(bar.progress)
                when (val outcome = router.applyBalance(value, state.capabilities())) {
                    is Router.Outcome.Moved -> {
                        state.balance = value
                        balanceLine.text = balanceLabel(value)
                    }
                    is Router.Outcome.Partial -> balanceLine.text = outcome.caveat
                    is Router.Outcome.Refused -> balanceLine.text = outcome.why
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        watcher.start()
        redraw()
    }

    override fun onPause() {
        super.onPause()
        watcher.stop()
    }

    // ---- probing --------------------------------------------------------------------------

    private fun probe() {
        if (!Shell.isRunning()) {
            shizukuLine.text = "Shizuku is not running. Start it, then probe again."
            return
        }
        if (!Shell.hasPermission()) {
            Shell.requestPermission(SHIZUKU_REQUEST)
            shizukuLine.text = "Asked Shizuku for permission. Allow it, then probe again."
            return
        }

        probeButton.text = "Probing…"
        probeButton.isEnabled = false

        Thread {
            val caps = Probe.runAll(packageName)
            state.saveCapabilities(caps)
            runOnUiThread {
                probeButton.text = "Probe again"
                probeButton.isEnabled = true
                redraw()
                Notifier.post(this)
            }
        }.start()
    }

    // ---- drawing --------------------------------------------------------------------------

    private fun buildProbeRows(ids: List<Triple<String, String, String>>) {
        probeRows.removeAllViews()
        ids.forEach { (id, title, buys) ->
            val row = layoutInflater.inflate(R.layout.probe_row, probeRows, false)
            row.tag = id
            row.findViewById<TextView>(R.id.probe_title).text = title
            row.findViewById<TextView>(R.id.probe_buys).text = buys
            probeRows.addView(row)
        }
    }

    private fun redraw() {
        val caps = state.capabilities()

        shizukuLine.text = when {
            !Shell.isRunning() -> "Shizuku is not running"
            !Shell.hasPermission() -> "Shizuku is running but has not allowed this app"
            else -> "Shizuku connected"
        }
        shizukuLine.setTextColor(
            color(if (Shell.isRunning() && Shell.hasPermission()) R.color.sand else R.color.slate_ink)
        )

        for (i in 0 until probeRows.childCount) {
            val row = probeRows.getChildAt(i)
            val id = row.tag as String
            val result = caps.results.firstOrNull { it.id == id }
            val verdict = result?.verdict ?: Verdict.UNTESTED

            row.findViewById<TextView>(R.id.probe_verdict).apply {
                text = when (verdict) {
                    Verdict.UNTESTED -> "not tried"
                    Verdict.WORKS -> "works"
                    Verdict.REFUSED -> "refused"
                    Verdict.ABSENT -> "absent"
                    Verdict.FAULT -> "fault"
                }
                // §3: colour carries state. FAULT is the only one that earns red, because a
                // capability this build simply does not have is not an incident.
                setTextColor(
                    color(
                        when (verdict) {
                            Verdict.WORKS -> R.color.amber
                            Verdict.FAULT -> R.color.fault
                            else -> R.color.slate_ink
                        }
                    )
                )
            }
            row.findViewById<TextView>(R.id.probe_detail).apply {
                text = result?.detail.orEmpty().ifEmpty { "—" }
                setTextColor(color(R.color.slate_ink))
            }
        }

        val balanceUsable = caps.works(Probe.BALANCE)
        balanceBar.isEnabled = balanceUsable
        balanceBar.progress = balanceToProgress(state.balance)
        balanceLine.text =
            if (balanceUsable) balanceLabel(state.balance)
            else "master_balance is not writable here — probe to find out"
        balanceLine.setTextColor(color(if (balanceUsable) R.color.sand else R.color.slate_ink))

        notifButton.text =
            if (needsNotificationPermission()) "Allow notifications" else "Show the switcher"
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun progressToBalance(progress: Int): Float =
        BlendMath.clampBalance((progress - 100) / 100f)

    private fun balanceToProgress(balance: Float): Int =
        ((BlendMath.clampBalance(balance) * 100f) + 100f).toInt()

    private fun balanceLabel(balance: Float): String = when {
        kotlin.math.abs(balance) < 0.02f -> "Centred"
        balance < 0 -> "Left ${(-balance * 100).toInt()}%"
        else -> "Right ${(balance * 100).toInt()}%"
    }

    private companion object {
        const val SHIZUKU_REQUEST = 7
    }
}
