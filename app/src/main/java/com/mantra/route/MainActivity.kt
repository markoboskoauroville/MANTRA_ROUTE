package com.mantra.route

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ImageView
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
    private lateinit var copyButton: TextView
    private lateinit var releaseButton: TextView
    private lateinit var callAudioLine: TextView
    private lateinit var patchHeaders: LinearLayout
    private lateinit var patchRows: LinearLayout
    private lateinit var patchLegend: TextView
    private lateinit var volumeRows: LinearLayout
    private lateinit var balanceReset: TextView
    private lateinit var arrangeRows: LinearLayout
    private lateinit var arrangeReset: TextView

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

        // Android 15 (API 35) made edge-to-edge mandatory for apps targeting 35+. The window
        // now runs under the status bar and the navigation bar, and a layout that does not
        // consume the insets draws behind the clock and behind the nav buttons — which is
        // exactly what happened. Pad the scrolling content by the system bar insets, and keep
        // clipToPadding off so the content still scrolls under them rather than being boxed in.
        val root = findViewById<android.view.View>(R.id.root_scroll)
        val basePadding = root.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top + basePadding, bars.right, bars.bottom + basePadding)
            insets
        }

        state = State(this)
        router = Router(this)

        // v7-v10 pinned the call route and left it pinned. Anyone upgrading arrives with the
        // earpiece still held and no reason to know it. Let go before the screen is drawn.
        router.releaseAnythingHeldAtStartup()
        watcher = OutputWatcher(this)

        shizukuLine = findViewById(R.id.shizuku_line)
        probeButton = findViewById(R.id.probe_button)
        probeRows = findViewById(R.id.probe_rows)
        balanceBar = findViewById(R.id.balance_bar)
        balanceLine = findViewById(R.id.balance_line)
        notifButton = findViewById(R.id.notif_button)
        copyButton = findViewById(R.id.copy_button)
        releaseButton = findViewById(R.id.release_button)
        callAudioLine = findViewById(R.id.call_audio_line)
        patchHeaders = findViewById(R.id.patch_headers)
        patchRows = findViewById(R.id.patch_rows)
        patchLegend = findViewById(R.id.patch_legend)
        volumeRows = findViewById(R.id.volume_rows)
        balanceReset = findViewById(R.id.balance_reset)
        arrangeRows = findViewById(R.id.arrange_rows)
        arrangeReset = findViewById(R.id.arrange_reset)

        arrangeReset.setOnClickListener {
            state.resetArrangement()
            redraw()
            Notifier.post(this)
        }

        findViewById<TextView>(R.id.version).text = "v" + BuildConfig.VERSION_NAME

        // The rows exist from the first frame, before anything has been probed, holding
        // UNTESTED. A screen that fills in is a screen that jumped.
        buildProbeRows(Probe.runAllTitlesOnly())

        probeButton.setOnClickListener { probe() }

        // The thing a person reaches for when the speakerphone has stopped working. It does not
        // depend on Shizuku, on a probe having been run, or on anything else being right.
        releaseButton.setOnClickListener {
            press(releaseButton, RELEASE_LABEL) {
                val outcome = router.releaseCallAudio()
                state.lastSelectedKey = ""
                Notifier.post(this)
                callAudioLine.text = router.callAudioReport()
                drawPatchBay()
                when (outcome) {
                    is Router.Outcome.Moved -> outcome.how
                    is Router.Outcome.Partial -> outcome.caveat
                    is Router.Outcome.Refused -> outcome.why
                }
            }
        }

        balanceReset.setOnClickListener {
            press(balanceReset, "Centre the balance") {
                val outcome = router.applyBalance(0f, state.capabilities())
                when (outcome) {
                    is Router.Outcome.Moved -> {
                        state.balance = 0f
                        balanceBar.progress = balanceToProgress(0f)
                        balanceLine.text = "Centred"
                        "Centred — balance is 0.0"
                    }
                    is Router.Outcome.Partial -> outcome.caveat
                    is Router.Outcome.Refused -> outcome.why
                }
            }
        }

        // A screenshot of this screen scrolls off the bottom, and the detail column is the part
        // that matters most when something has gone wrong — the v2 fault was diagnosable only
        // because the exception class was visible. So the whole thing goes to the clipboard as
        // text, in one press.
        copyButton.setOnClickListener {
            press(copyButton, "Copy the report") {
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Mantra Route report", report())
                )
                "Copied — paste it anywhere"
            }
        }

        notifButton.setOnClickListener {
            if (needsNotificationPermission()) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                press(notifButton, "Show the switcher") {
                    Notifier.post(this)
                    "Panel posted — pull down the shade"
                }
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

    /**
     * §7 again: this list holds every output the phone has ever reported in this session,
     * ticked or not. Unticking one takes it out of the notification and nothing else.
     */
    private fun drawArrangeRows() {
        val rows = router.allRows()
        arrangeRows.removeAllViews()
        if (rows.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No outputs reported yet"
                setTextColor(color(R.color.slate_ink))
                textSize = 13f
            }
            arrangeRows.addView(empty)
            return
        }
        val off = state.switchedOff
        rows.forEach { row ->
            val view = layoutInflater.inflate(R.layout.arrange_row, arrangeRows, false)
            view.findViewById<TextView>(R.id.arrange_label).apply {
                text = row.label
                setTextColor(color(if (row.key in off) R.color.slate_ink else R.color.sand))
            }
            view.findViewById<ImageView>(R.id.arrange_glyph)
                .setImageResource(glyphRes(row.glyph))
            view.findViewById<CheckBox>(R.id.arrange_shown).apply {
                setOnCheckedChangeListener(null)
                isChecked = row.key !in off
                setOnCheckedChangeListener { _, _ ->
                    state.toggleOff(row.key)
                    drawArrangeRows()
                    Notifier.post(this@MainActivity)
                }
            }
            arrangeRows.addView(view)
        }
    }

    private fun glyphRes(glyph: Glyph): Int = when (glyph) {
        Glyph.SPEAKER -> R.drawable.ic_out_speaker
        Glyph.EARPIECE -> R.drawable.ic_out_earpiece
        Glyph.WIRED -> R.drawable.ic_out_wired
        Glyph.USB -> R.drawable.ic_out_usb
        Glyph.BLUETOOTH -> R.drawable.ic_out_bluetooth
        Glyph.BLE -> R.drawable.ic_out_ble
        Glyph.HEARING_AID -> R.drawable.ic_out_hearing
        Glyph.HDMI -> R.drawable.ic_out_hdmi
        Glyph.DOCK -> R.drawable.ic_out_dock
    }

    private fun redraw() {
        drawArrangeRows()
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

        callAudioLine.text = router.callAudioReport()
        drawPatchBay()
        drawVolumes()

        notifButton.text =
            if (needsNotificationPermission()) "Allow notifications" else "Show the switcher"
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    /**
     * The press rule, and it is the same rule for every control on this screen.
     *
     * Reported 23.8.2026: pressing Fix call audio and the reset produced no visible response, so
     * there was no way to know the press had registered. The copy button did respond — but by
     * accident, because it happened to have something to say.
     *
     * So: three channels at once, because any one of them can be missed. The label becomes the
     * RESULT and holds long enough to read. The button flashes amber. The device gives a haptic
     * tick, which is the only one that works when you are not looking at the screen.
     */
    private fun press(button: TextView, restingLabel: String, run: () -> String) {
        button.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        val message = Feedback.resultLabel(run())
        button.text = message
        button.setTextColor(color(R.color.amber))
        button.postDelayed({
            button.text = restingLabel
            button.setTextColor(color(R.color.sand))
        }, Feedback.HOLD_MS)
    }

    /** Draw one slider per stream, each showing its actual index out of its actual range. */
    private fun drawVolumes() {
        val caps = state.capabilities()
        volumeRows.removeAllViews()
        Volume.STREAMS.forEach { stream ->
            val view = layoutInflater.inflate(R.layout.volume_row, volumeRows, false)
            val label = view.findViewById<TextView>(R.id.volume_label)
            val bar = view.findViewById<SeekBar>(R.id.volume_bar)

            val max = router.volumeMax(stream.id)
            val index = router.volumeIndex(stream.id)
            label.text = Volume.label(stream.label, index, max)
            // The stuck-low case, named as a level rather than left to read as a dead route.
            label.setTextColor(color(if (Volume.isLow(index, max)) R.color.fault else R.color.sand))
            bar.progress = Volume.percentFor(index, max)
            bar.isEnabled = max > 0

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) label.text = Volume.label(stream.label, Volume.indexFor(p, max), max)
                }
                override fun onStartTrackingTouch(b: SeekBar) = Unit
                override fun onStopTrackingTouch(b: SeekBar) {
                    b.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    val wanted = Volume.indexFor(b.progress, max)
                    val outcome = router.setVolume(stream.id, wanted, caps)
                    val now = router.volumeIndex(stream.id)
                    label.text = when (outcome) {
                        is Router.Outcome.Moved -> Volume.label(stream.label, now, max)
                        is Router.Outcome.Partial -> outcome.caveat
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

    /**
     * Draw the crosspoint grid.
     *
     * Rebuilt on every redraw because the destination COLUMNS change when a headset connects —
     * this is a different case from §1's "nothing appears": the grid's shape is the data. What
     * §1 forbids is a control that vanishes; a blocked crosspoint stays drawn and stays dim.
     */
    private fun drawPatchBay() {
        val caps = state.capabilities()
        val routingWorks = caps.anyRouting
        val rows = router.allRows()

        patchHeaders.removeAllViews()
        rows.forEach { row ->
            val header = layoutInflater.inflate(R.layout.patch_cell, patchHeaders, false) as TextView
            header.text = Chip.short(row.label, 7)
            header.textSize = 11f
            header.setTextColor(color(R.color.slate_ink))
            patchHeaders.addView(header)
        }

        patchRows.removeAllViews()
        listOf(Path.MEDIA to "Media").forEach { (path, name) ->
            val rowView = layoutInflater.inflate(R.layout.patch_row, patchRows, false)
            rowView.findViewById<TextView>(R.id.path_label).text = name
            rowView.findViewById<TextView>(R.id.path_why).text = PatchBay.why(routingWorks)
            val cells = rowView.findViewById<LinearLayout>(R.id.cells)

            rows.forEach { row ->
                val isCurrent = routingWorks && row.key == state.lastSelectedKey
                val cell = PatchBay.cell(row.typeCode, routingWorks, isCurrent)
                val view = layoutInflater.inflate(R.layout.patch_cell, cells, false) as TextView
                view.text = PatchBay.mark(cell)
                view.setTextColor(
                    color(
                        when (cell) {
                            Cell.CONNECTED -> R.color.amber
                            Cell.CONNECTABLE -> R.color.sand
                            Cell.BLOCKED -> R.color.slate_ink
                        }
                    )
                )
                if (cell != Cell.BLOCKED) {
                    view.setOnClickListener { patch(path, row) }
                } else {
                    // Still tappable, but it explains itself instead of doing nothing silently.
                    view.setOnClickListener {
                        patchLegend.text = if (!Claim.carriesMedia(row.typeCode)) {
                            "${row.label} cannot carry music — Android sends only calls there"
                        } else {
                            "no routing privilege on this phone, so media cannot be patched"
                        }
                    }
                }
                cells.addView(view)
            }
            patchRows.addView(rowView)
        }

        patchLegend.text = "●  patched     ○  free     ·  not possible"
    }

    private fun patch(path: Path, row: Row) {
        val outcome = router.selectOutput(row.id, state.capabilities())
        patchLegend.text = when (outcome) {
            is Router.Outcome.Moved -> "${row.label}: ${outcome.how}"
            is Router.Outcome.Partial -> "${row.label}: ${outcome.caveat}"
            is Router.Outcome.Refused -> "${row.label}: ${outcome.why}"
        }
        if (outcome !is Router.Outcome.Refused) state.lastSelectedKey = row.key
        Notifier.post(this)
        drawPatchBay()
    }

    /** The screen, as plain text. Everything, including the probes that scrolled off. */
    private fun report(): String {
        val caps = state.capabilities()
        val builder = StringBuilder()
        builder.append("Mantra Route v").append(BuildConfig.VERSION_NAME).append('\n')
        builder.append("Android ").append(Build.VERSION.SDK_INT)
            .append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        builder.append("Shizuku: ").append(shizukuLine.text).append("\n\n")

        // allRows(), not rows(switchedOff): a report that silently omitted the outputs you had
        // hidden would be a report that lies by omission, and hiding one is exactly the sort of
        // thing you would forget having done.
        builder.append("OUTPUTS\n")
        val off = state.switchedOff
        val rows = router.allRows()
        if (rows.isEmpty()) builder.append("  (none reported)\n")
        rows.forEach { row ->
            builder.append("  ").append(row.label).append("  [").append(row.glyph).append("]")
            if (row.key in off) builder.append("  (hidden)")
            builder.append('\n')
        }

        builder.append("\nVOLUME\n")
        Volume.STREAMS.forEach { st ->
            val max = router.volumeMax(st.id)
            val idx = router.volumeIndex(st.id)
            builder.append("  ").append(Volume.label(st.label, idx, max))
            if (Volume.isLow(idx, max)) builder.append("   <-- LOW")
            builder.append('\n')
        }

        builder.append("\nCALL AUDIO\n")
        builder.append(router.callAudioReport()).append('\n')

        builder.append("\nPROBES\n")
        Probe.runAllTitlesOnly().forEach { (id, title, _) ->
            val r = caps.results.firstOrNull { it.id == id }
            builder.append("  ").append(title).append(": ")
                .append(r?.verdict?.name ?: "UNTESTED").append('\n')
            val detail = r?.detail.orEmpty()
            if (detail.isNotEmpty()) builder.append("      ").append(detail).append('\n')
        }
        return builder.toString()
    }

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
        const val RELEASE_LABEL = "Release call audio"
        const val SHIZUKU_REQUEST = 7
    }
}
