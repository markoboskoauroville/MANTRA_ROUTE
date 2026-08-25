package com.mantra.route

import android.content.Context
import android.media.AudioManager

/**
 * Volume, and nothing else.
 *
 * v18 removed Shizuku entirely. What is left needs no privileged shell, no probe and no
 * permission beyond one that is granted on the phone itself — which is what Baba asked for on
 * 25.8.2026 and, as v16 established, what the volume tiles always could have had.
 *
 * Gone with it: mono downmix, left/right balance, the patch bay, the capability probe, the call
 * release, and the whole "what this phone allows" screen. Every one of those depended on a
 * shell started over adb, which means Wireless debugging, which means Wi-Fi, which he often
 * does not have. A feature that cannot be reached is not a feature.
 */
class Router(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    fun volumeMax(streamId: Int): Int =
        runCatching { audio.getStreamMaxVolume(streamId) }.getOrDefault(0)

    fun volumeIndex(streamId: Int): Int =
        runCatching { audio.getStreamVolume(streamId) }.getOrDefault(0)

    sealed class Outcome {
        data class Moved(val how: String) : Outcome()
        data class Refused(val why: String) : Outcome()
    }

    /**
     * Set a stream's level, then READ IT BACK.
     *
     * `setStreamVolume` returns void and reports nothing, so without the read-back this would
     * be another control that looks like it worked. It is guarded by no permission and throws
     * in exactly one case: Do Not Disturb is on and the app has no notification-policy access,
     * which affects Ring and Notification only. That exception is NAMED rather than swallowed —
     * the old code discarded it and blamed Shizuku, sending anyone reading it to fix the wrong
     * thing entirely.
     */
    fun setVolume(streamId: Int, index: Int): Outcome {
        val max = volumeMax(streamId)
        if (max <= 0) return Outcome.Refused("this stream reports no range on this device")
        val wanted = index.coerceIn(0, max)

        val attempt = runCatching { audio.setStreamVolume(streamId, wanted, 0) }
        if (volumeIndex(streamId) == wanted) return Outcome.Moved("set to $wanted of $max")

        return if (attempt.exceptionOrNull() is SecurityException) {
            Outcome.Refused("Do Not Disturb is blocking this — grant Do Not Disturb access")
        } else {
            Outcome.Refused("the platform refused $wanted of $max")
        }
    }
}
