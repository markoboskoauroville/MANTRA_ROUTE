package com.mantra.route

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.util.TypedValue
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

    /** Stream, its name label, its slider, and the thumb that carries the number. */
    private val rows = mutableListOf<Row>()

    private data class Row(
        val stream: Stream,
        val label: TextView,
        val bar: SeekBar,
        val thumb: ThumbDrawable,
    )

    /** The glyph for each channel, matched to the one the system volume panel uses. */
    private fun glyphFor(streamId: Int) = when (streamId) {
        0 -> R.drawable.ic_stream_call
        3 -> R.drawable.ic_stream_media
        2 -> R.drawable.ic_stream_ring
        5 -> R.drawable.ic_stream_notification
        else -> R.drawable.ic_stream_alarm
    }

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
            view.findViewById<ImageView>(R.id.volume_glyph).setImageResource(glyphFor(stream.id))

            val thumbPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics,
            ).toInt()
            val thumb = ThumbDrawable(thumbPx)
            bar.thumb = thumb

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                    // refreshVolumes() sets progress itself; without this guard every observer
                    // callback would redraw the label from a half-applied value.
                    // The number follows the finger, so it updates on every pixel of the drag
                    // rather than on release. Setting the VOLUME on every pixel would be a
                    // system call per pixel; drawing a number is not.
                    if (!fromUser) return
                    thumb.label = p.toString()
                }

                override fun onStartTrackingTouch(b: SeekBar) = Unit

                /** Applied on release: each apply is a real system call. */
                override fun onStopTrackingTouch(b: SeekBar) {
                    b.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val max = router.volumeMax(stream.id)
                    val outcome = router.setVolume(stream.id, Volume.indexFor(b.progress, max))

                    // Refresh FIRST, then write any refusal over the top of it. The other order
                    // sets the message and then immediately overwrites it with the channel name
                    // — the refusal would have flashed and vanished, which is indistinguishable
                    // from the slider having worked.
                    refreshVolumes()
                    if (outcome is Router.Outcome.Refused) {
                        label.text = outcome.why
                        label.setTextColor(color(R.color.fault))
                    }

                    // The tiles cannot see this happen. Tell them.
                    TileNudge.all(this@MainActivity)
                }
            })
            volumeRows.addView(view)
            rows.add(Row(stream, label, bar, thumb))
        }
    }

    private fun refreshVolumes() {
        rows.forEach { row ->
            val max = router.volumeMax(row.stream.id)
            val index = router.volumeIndex(row.stream.id)
            val percent = Volume.percentFor(index, max)
            row.label.text = Volume.label(row.stream.label, max)
            row.label.setTextColor(
                color(if (Volume.isLow(index, max)) R.color.fault else R.color.sand)
            )
            row.bar.isEnabled = max > 0
            row.bar.progress = percent
            row.thumb.label = if (max <= 0) "--" else percent.toString()
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
}
