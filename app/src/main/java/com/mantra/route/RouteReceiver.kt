package com.mantra.route

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Every tap on the notification lands here.
 *
 * A tap does three things in this order: try the change, tell the truth about what happened,
 * redraw. The redraw is not optional — the row that lights up has to be the one the platform
 * agrees is carrying sound, and the only way to know that is to ask again after acting.
 *
 * onReceive runs on the main thread and the proxy-router rung waits up to two seconds for
 * routes to arrive, so the work is moved off it with goAsync(). Doing it inline froze the
 * shade for the length of the wait, which reads as the notification being broken.
 */
class RouteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val action = intent.action
        val deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1)
        val blendName = intent.getStringExtra(EXTRA_BLEND)

        Thread {
            try {
                handle(context, action, deviceId, blendName)
                Notifier.post(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, action: String?, deviceId: Int, blendName: String?) {
        val router = Router(context)
        val state = State(context)
        val caps = state.capabilities()

        when (action) {
            ACTION_SELECT -> {
                val key = router.allRows().firstOrNull { it.id == deviceId }?.key
                when (val outcome = router.selectOutput(deviceId, caps)) {
                    is Router.Outcome.Moved -> {
                        if (key != null) state.lastSelectedKey = key
                        say(context, "Switched — ${outcome.how}")
                    }
                    is Router.Outcome.Partial -> {
                        if (key != null) state.lastSelectedKey = key
                        say(context, outcome.caveat)
                    }
                    is Router.Outcome.Refused -> {
                        // Better one tap into the platform's own picker than a dead row.
                        say(context, outcome.why)
                        runCatching { context.startActivity(router.systemPickerIntent()) }
                    }
                }
            }

            ACTION_BLEND -> {
                val blend = runCatching { Blend.valueOf(blendName ?: "") }
                    .getOrDefault(Blend.STEREO)
                when (val outcome = router.applyBlend(blend, caps)) {
                    is Router.Outcome.Moved -> {
                        state.blend = blend
                        say(context, outcome.how)
                    }
                    is Router.Outcome.Partial -> say(context, outcome.caveat)
                    is Router.Outcome.Refused -> say(context, outcome.why)
                }
            }

            // The notification does not survive a reboot on its own. Nothing is re-applied
            // here: master_mono and master_balance are secure settings and are still where
            // they were left, and Shizuku will not be running this early anyway.
            Intent.ACTION_BOOT_COMPLETED, ACTION_REFRESH -> Unit
        }
    }

    private fun say(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_PICKER = "com.mantra.route.PICKER"
        const val ACTION_SELECT = "com.mantra.route.SELECT"
        const val ACTION_BLEND = "com.mantra.route.BLEND"
        const val ACTION_REFRESH = "com.mantra.route.REFRESH"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_BLEND = "blend"
    }
}

/**
 * Outputs appear and vanish while the notification is on screen. Without this the list is only
 * as current as the last tap, which is exactly when a person reaches for a headset that has
 * just connected and finds it is not listed.
 *
 * It also puts the sound back. Plugging the same headphones in again should return to them,
 * and that only works because the choice was stored against a stable key rather than an id.
 */
class OutputWatcher(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
            Thread {
                val state = State(context)
                val key = state.lastSelectedKey
                if (key.isNotEmpty()) {
                    Router(context).reselect(key, state.capabilities())
                }
                Notifier.post(context)
            }.start()
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
            Notifier.post(context)
        }
    }

    fun start() = audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    fun stop() = audio.unregisterAudioDeviceCallback(callback)
}
