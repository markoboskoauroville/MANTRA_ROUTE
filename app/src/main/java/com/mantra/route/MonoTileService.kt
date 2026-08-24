package com.mantra.route

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile.
 *
 * Asked for on 24.8.2026, pointing at the panel that drops down from the top of the screen.
 * That panel is Quick Settings; a button an app puts there is a Quick Settings tile, and this
 * is the class that makes one.
 *
 * It carries the single control that measurably works on this phone and is worth reaching in
 * one gesture: the mono downmix. Not a launcher shortcut — the app is already in the launcher,
 * and a tile that only opens an app has spent a slot in the most valuable strip of screen there
 * is on doing nothing.
 *
 * The subtitle shows the live state, so the tile answers the question without being pressed.
 */
class MonoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        paint()
    }

    override fun onClick() {
        super.onClick()
        val router = Router(this)
        val state = State(this)
        val caps = state.capabilities()

        // Read the phone, not the stored belief. §14, and the bug that produced it.
        val nowMono = router.currentBlend() == Blend.MONO
        val wanted = if (nowMono) Blend.STEREO else Blend.MONO

        when (val outcome = router.applyBlend(wanted, caps)) {
            is Router.Outcome.Moved -> state.blend = wanted
            is Router.Outcome.Partial -> Unit
            is Router.Outcome.Refused -> {
                // Refusing quietly would make the tile look broken. The subtitle says why.
                qsTile?.apply {
                    state = Tile.STATE_UNAVAILABLE
                    subtitle = if (!Shell.isRunning()) "Shizuku not running" else "not permitted"
                    updateTile()
                }
                return
            }
        }
        paint()
        Notifier.post(this)
    }

    private fun paint() {
        val tile = qsTile ?: return
        val router = Router(this)
        val available = State(this).capabilities().works(Probe.MONO) && Shell.isRunning()
        val mono = runCatching { router.currentBlend() == Blend.MONO }.getOrDefault(false)

        tile.state = when {
            !available -> Tile.STATE_UNAVAILABLE
            mono -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "Mono audio"
        tile.subtitle = when {
            !Shell.isRunning() -> "Shizuku off"
            !available -> "probe first"
            mono -> "Mono"
            else -> "Stereo"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.updateTile()
    }
}
