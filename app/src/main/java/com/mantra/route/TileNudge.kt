package com.mantra.route

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

/**
 * Tell every tile to re-read the volume, now.
 *
 * A tile that is not ACTIVE_TILE repaints when the panel opens and after its own press, and at
 * no other time. So a change made on the app's sliders left the tiles showing a level that was
 * no longer true — for as long as the panel stayed shut, which could be hours.
 *
 * `requestListeningState` is the platform's answer: it binds the service and calls
 * onStartListening, which is where paint() already lives. Nothing else in the tile needs to
 * know it happened.
 */
object TileNudge {

    private val TILES = listOf(
        CallVolumeTileService::class.java,
        MediaVolumeTileService::class.java,
        RingVolumeTileService::class.java,
        NotificationVolumeTileService::class.java,
        AlarmVolumeTileService::class.java,
    )

    fun all(context: Context) {
        TILES.forEach { cls ->
            // A tile the person never placed still accepts this; it simply has nothing to draw.
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, cls))
            }
        }
    }
}
