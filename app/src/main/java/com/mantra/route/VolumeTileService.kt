package com.mantra.route

import android.graphics.drawable.Icon
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

    override fun onStartListening() {
        super.onStartListening()
        paint()
    }

    override fun onClick() {
        super.onClick()
        val router = Router(this)
        val max = router.volumeMax(stream.id)

        // Read the level off the phone at the moment of the press. A tile is listened to and
        // then left alone for hours; anything cached here would be a guess about the past.
        val now = Presets.snap(router.volumeIndex(stream.id).let { Volume.percentFor(it, max) }, max)
        val wanted = Presets.next(now)

        when (val outcome = router.setVolume(stream.id, Volume.indexFor(wanted, max))) {
            is Router.Outcome.Moved -> {
                paint()
                say(TileText.spoken(stream.label, wanted, max))
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
