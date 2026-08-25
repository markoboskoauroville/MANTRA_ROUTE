package com.mantra.route

import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * One Quick Settings tile per stream, cycling 25 → 50 → 75 → 100 → 25.
 *
 * v21 halved the tile count. Two tiles per channel needed a range printed on each face to tell
 * them apart, and that range cost the top third of a small circle to say something the person
 * pressing already knew. One tile, four steps, and the face spends everything it has on the two
 * things that change: which channel, and where it is.
 *
 * COLOUR IS SPENT ONCE. The tile is dark at every level except 100, where it goes white. It is
 * the only state worth a channel of its own — full is the one you can hear coming — and it is
 * also what disambiguates the digit "1", which means 100 when the tile is white and a tenth
 * when it is dark.
 */
abstract class VolumeTileService : TileService() {

    abstract val stream: Stream

    /**
     * Which way the elevator is travelling, per stream.
     *
     * It cannot be derived from the level: at 50, up and down are both legitimate. It is the
     * only thing this app persists, it is one boolean, and if it is ever lost the tile simply
     * starts going up again — a wrong guess that corrects itself on the next press.
     */
    private fun goingUp(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(), true)

    private fun setGoingUp(context: Context, up: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key(), up).apply()
    }

    private fun key() = "up_" + stream.id

    /**
     * While the panel is open, follow the volume wherever it is changed from.
     *
     * onStartListening fires when the panel opens and at no other time, so a change made with
     * the hardware keys, or on the app's sliders, or by another tile, left this face showing a
     * level that was no longer true — for as long as the panel stayed open.
     *
     * The settings table is the one place all of those routes write to.
     */
    private val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = paint()
    }

    override fun onStartListening() {
        super.onStartListening()
        runCatching {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, watcher)
        }
        paint()
    }

    /** Unregistered as soon as the panel closes: an observer on a tile nobody is looking at. */
    override fun onStopListening() {
        runCatching { contentResolver.unregisterContentObserver(watcher) }
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val router = Router(this)
        val max = router.volumeMax(stream.id)

        // Read the level off the phone at the moment of the press. A tile is listened to and
        // then left alone for hours; anything cached here would be a guess about the past.
        //
        // The next STEP, not the next percentage: a coarse stream cannot represent all four
        // presets, and cycling on percentages leaves such a stream stuck on one level with
        // every press appearing to work.
        val currentIndex = router.volumeIndex(stream.id)
        val step = Elevator.step(currentIndex, max, goingUp(this))

        when (val outcome = router.setVolume(stream.id, step.index)) {
            is Router.Outcome.Moved -> {
                setGoingUp(this, step.goingUp)
                paint()
                // Say what the phone ended up at, not what was asked for. On a stream that
                // cannot land exactly on a preset the two differ, and the bubble must report
                // the level that is actually in force.
                val landed = Presets.snap(Volume.percentFor(router.volumeIndex(stream.id), max), max)
                // The banner if it is allowed, the toast if it is not. Never neither: a press
                // with no acknowledgement is the complaint this whole feature answers.
                if (StatusBanner.canShow(this)) {
                    StatusBanner.show(this, TileText.banner(stream.label, landed, max))
                } else {
                    say(TileText.spoken(stream.label, landed, max))
                }
            }
            is Router.Outcome.Refused -> {
                say(outcome.why)
                qsTile?.let { tile ->
                    tile.subtitle = outcome.why.take(40)
                    tile.updateTile()
                }
            }
        }
    }

    /**
     * The bubble, on every press.
     *
     * A whole sentence rather than a number, because the tile is small and pressed without
     * looking: "Call 100%" says what was touched as well as what happened, and the wrong tile
     * pressed is exactly the case a bare "100%" would hide.
     */
    private fun say(sentence: String) {
        runCatching { Toast.makeText(applicationContext, sentence, Toast.LENGTH_SHORT).show() }
    }

    /**
     * Draw the level that is ACTUALLY set, not the nearest preset.
     *
     * The face follows the app's sliders wherever they are put: 63% shows a 6. `snap` only
     * rounds when the measured level is within half a step of a preset, which is the case where
     * the hardware could not land on it exactly — 26.7% on the fifteen-step Call stream is
     * shown as 25 because 25 is what it was asked for and the closest it can get.
     *
     * Pressing is what snaps to a preset. Displaying does not.
     */
    private fun paint() {
        val tile = qsTile ?: return
        val router = Router(this)
        val max = router.volumeMax(stream.id)
        val percent = Presets.snap(Volume.percentFor(router.volumeIndex(stream.id), max), max)

        tile.state = when {
            max <= 0 -> Tile.STATE_UNAVAILABLE
            Presets.isFull(percent) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = TileText.label(stream.label, percent, max)
        tile.subtitle = null
        tile.icon = Icon.createWithBitmap(TileIcon.render(TileText.face(stream.label, percent, max)))
        tile.updateTile()
    }
}

private const val PREFS = "mantra_route_tiles"

/** Five tiles, one per stream. Android requires a distinct class for each. */
class CallVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(0)   // STREAM_VOICE_CALL
}

class MediaVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(3)   // STREAM_MUSIC
}

class RingVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(2)   // STREAM_RING
}

class NotificationVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(5)   // STREAM_NOTIFICATION
}

class AlarmVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(4)   // STREAM_ALARM
}
