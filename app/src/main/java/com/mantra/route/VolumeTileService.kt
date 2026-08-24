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
    abstract val glyphRes: Int

    override fun onStartListening() {
        super.onStartListening()
        paint()
    }

    override fun onClick() {
        super.onClick()
        val router = Router(this)
        val caps = State(this).capabilities()

        val max = router.volumeMax(stream.id)
        // Read the level off the phone at the moment of the press. A tile is listened to and
        // then left alone for hours; anything cached here would be a guess about the past.
        val now = router.volumeIndex(stream.id)

        when (val outcome = router.setVolume(stream.id, VolumeToggle.target(now, max), caps)) {
            is Router.Outcome.Moved, is Router.Outcome.Partial -> paint()
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
            VolumeToggle.atFull(index, max) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = TileText.label(stream.label, index, max)
        tile.subtitle = TileText.nextAction(index, max)
        tile.icon = Icon.createWithBitmap(
            TileIcon.render(this, glyphRes, TileText.badge(index, max))
        )
        tile.updateTile()
    }
}

/**
 * Five tiles, named and glyphed to match the system volume panel exactly — Media, not Music,
 * and the ringing-phone glyph for Ring rather than the bell, which belongs to Notification.
 * Borrowing the platform's own vocabulary means nothing has to be translated.
 */
class CallVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(0)   // STREAM_VOICE_CALL
    override val glyphRes = R.drawable.ic_stream_call
}

class MediaVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(3)   // STREAM_MUSIC
    override val glyphRes = R.drawable.ic_stream_media
}

class RingVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(2)   // STREAM_RING
    override val glyphRes = R.drawable.ic_stream_ring
}

class NotificationVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(5)   // STREAM_NOTIFICATION
    override val glyphRes = R.drawable.ic_stream_notification
}

class AlarmVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(4)   // STREAM_ALARM
    override val glyphRes = R.drawable.ic_stream_alarm
}
