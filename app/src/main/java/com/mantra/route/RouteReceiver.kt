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
 */
class RouteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val router = Router(context)
        val state = State(context)
        val caps = state.capabilities()

        when (intent.action) {
            ACTION_SELECT -> {
                val id = intent.getIntExtra(EXTRA_DEVICE_ID, -1)
                when (val outcome = router.selectOutput(id, caps)) {
                    is Router.Outcome.Moved -> {
                        state.lastSelectedId = id
                        say(context, "Switched — ${outcome.how}")
                    }
                    is Router.Outcome.Partial -> {
                        state.lastSelectedId = id
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
                val blend = runCatching {
                    Blend.valueOf(intent.getStringExtra(EXTRA_BLEND) ?: "")
                }.getOrDefault(Blend.STEREO)

                when (val outcome = router.applyBlend(blend, caps)) {
                    is Router.Outcome.Moved -> {
                        state.blend = blend
                        say(context, outcome.how)
                    }
                    is Router.Outcome.Partial -> say(context, outcome.caveat)
                    is Router.Outcome.Refused -> say(context, outcome.why)
                }
            }

            ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED -> Unit
        }

        Notifier.post(context)
    }

    private fun say(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
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
 */
class OutputWatcher(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
            Notifier.post(context)
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
            Notifier.post(context)
        }
    }

    fun start() = audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    fun stop() = audio.unregisterAudioDeviceCallback(callback)
}
