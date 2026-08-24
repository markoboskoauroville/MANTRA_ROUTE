package com.mantra.route

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * One Quick Settings tile per volume stream, toggling between 50% and 100%.
 *
 * Replaces MonoTileService. Mono was my judgement of what deserved the slot; a level toggle for
 * each stream is what was actually asked for, and it is the better use of the strip — the mono
 * setting is something you change twice a year, a volume is something you change hourly.
 *
 * Four subclasses rather than four copies: Android requires a distinct class per tile, so the
 * class is the only thing that differs and everything else is inherited.
 */
abstract class VolumeTileService : TileService() {

    abstract val stream: Stream
    abstract val iconRes: Int

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
        val wanted = VolumeToggle.target(now, max)

        when (val outcome = router.setVolume(stream.id, wanted, caps)) {
            is Router.Outcome.Moved, is Router.Outcome.Partial -> paint()
            is Router.Outcome.Refused -> {
                // A tile that refuses silently looks broken. Say it in the subtitle.
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
        tile.label = stream.label
        tile.subtitle = VolumeToggle.subtitle(index, max)
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.updateTile()
    }
}

class CallVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(0)   // STREAM_VOICE_CALL
    override val iconRes = R.drawable.ic_stream_call
}

class MusicVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(3)   // STREAM_MUSIC
    override val iconRes = R.drawable.ic_stream_music
}

class RingVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(2)   // STREAM_RING
    override val iconRes = R.drawable.ic_stream_ring
}

class AlarmVolumeTileService : VolumeTileService() {
    override val stream = Volume.byId(4)   // STREAM_ALARM
    override val iconRes = R.drawable.ic_stream_alarm
}
