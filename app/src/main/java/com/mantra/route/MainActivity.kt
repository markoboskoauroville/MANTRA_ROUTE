package com.mantra.route

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Sliders, and a way to the settings. Nothing else.
 *
 * Every title, caption and credit went to the settings screen. They were read once and then
 * occupied the screen for ever, pushing the only controls that matter into the bottom third.
 * The five rows now share the full height, so each is as tall as the phone allows and the
 * thumb is something a thumb can find.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var router: Router
    private lateinit var volumeRows: LinearLayout

    private val rows = mutableListOf<Triple<Stream, TextView, SeekBar>>()

    /**
     * Volume changes from the tiles, the hardware keys and the system panel, and none of them
     * tell this screen. The settings table is the one place all of them write to.
     */
    private val volumeWatcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshVolumes()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        router = Router(this)

        // Android 15 made edge-to-edge mandatory for targetSdk 35+; without consuming the
        // insets this draws behind the clock and behind the navigation buttons.
        val root = findViewById<View>(R.id.root_scroll)
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

        volumeRows = findViewById(R.id.volume_rows)
        findViewById<ImageView>(R.id.settings_button).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        buildVolumeRows()
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeWatcher)
        refreshVolumes()
    }

    override fun onPause() {
        super.onPause()
        runCatching { contentResolver.unregisterContentObserver(volumeWatcher) }
    }

    /** Build the rows once; their values are filled in by refreshVolumes(). */
    private fun buildVolumeRows() {
        volumeRows.removeAllViews()
        rows.clear()
        Volume.STREAMS.forEach { stream ->
            val view = layoutInflater.inflate(R.layout.volume_row, volumeRows, false)
            // Each row takes an equal share of whatever height the screen has.
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            val label = view.findViewById<TextView>(R.id.volume_label)
            val bar = view.findViewById<SeekBar>(R.id.volume_bar)

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                    // refreshVolumes() sets progress itself; without this guard every observer
                    // callback would redraw the label from a half-applied value.
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
                    // NO BANNER HERE. The label under the thumb already says the number, and a
                    // line across the top of the screen while you are dragging is covering the
                    // thing you are looking at. The banner is for a tile press, where there is
                    // nothing else to read.
                    TileNudge.all(this@MainActivity)
                }
            })
            volumeRows.addView(view)
            rows.add(Triple(stream, label, bar))
        }
    }

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
}
