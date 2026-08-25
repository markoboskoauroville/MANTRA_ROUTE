package com.mantra.route

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * One Quick Settings tile per volume stream, toggling between 50% and 100%.
 *
 * The tiles speak on four channels for one fact, because the screenshot showed the panel
 * drawing them as bare circles with no label and no subtitle — a two-state toggle that cannot
 * be read is not a toggle, it is a coin flip:
 *
 *     SHAPE     the glyph, matched to the one the system volume panel uses for that stream
 *     NUMBER    the percentage, drawn INTO the icon, because that square is all there is
 *     WORDS     label and subtitle, for the expanded panel and for a screen reader
 *     TOUCH     a haptic tick on press
 *
 * One channel failing is then a nuisance rather than a blank circle nobody can identify.
 */
abstract class VolumeTileService : TileService() {

    abstract val stream: Stream

    /** Which two levels this tile moves between. The class carries it; the logic does not care. */
    abstract val pair: TogglePair

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
        val now = router.volumeIndex(stream.id)

        when (val outcome = router.setVolume(stream.id, VolumeToggle.target(now, max, pair))) {
            is Router.Outcome.Moved -> paint()
            is Router.Outcome.Refused -> {
                // A tile that refuses silently looks broken. Say it where it can be seen.
                qsTile?.let { tile ->
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.subtitle = outcome.why.take(40)
                    tile.updateTile()
                }
            }
        }
    }

    private fun paint() {
        val tile = qsTile ?: return
        val router = Router(this)
        val max = router.volumeMax(stream.id)
        val index = router.volumeIndex(stream.id)

        tile.state = when {
            max <= 0 -> Tile.STATE_UNAVAILABLE
            VolumeToggle.atHigh(index, max, pair) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = TileText.label(stream.label, index, max, pair)
        tile.subtitle = TileText.nextAction(index, max, pair)
        tile.icon = Icon.createWithBitmap(
            TileIcon.render(
                TileText.range(pair),
                TileText.badge(index, max, pair),
                TileText.four(stream.label),
            )
        )
        tile.updateTile()
    }
}

/**
 * Ten concrete tiles: five streams, two toggle pairs each.
 *
 * Android requires a distinct class per tile, so this is the only place the combinations can
 * live. Ten is more than anyone will place — the picker shows them all and four or five get
 * dragged out. That clutter is the cost of not guessing which four were wanted, and it is paid
 * once, in a screen visited rarely.
 *
 * The LOUD family toggles 100/50, the QUIET family 50/25.
 */

class CallVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(0)
    override val pair = VolumeToggle.LOUD
}

class CallQuietVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(0)
    override val pair = VolumeToggle.QUIET
}

class MediaVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(3)
    override val pair = VolumeToggle.LOUD
}

class MediaQuietVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(3)
    override val pair = VolumeToggle.QUIET
}

class RingVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(2)
    override val pair = VolumeToggle.LOUD
}

class RingQuietVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(2)
    override val pair = VolumeToggle.QUIET
}

class NotificationVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(5)
    override val pair = VolumeToggle.LOUD
}

class NotificationQuietVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(5)
    override val pair = VolumeToggle.QUIET
}

class AlarmVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(4)
    override val pair = VolumeToggle.LOUD
}

class AlarmQuietVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(4)
    override val pair = VolumeToggle.QUIET
}
