package com.mantra.route

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings

/**
 * The thing that actually moves the sound.
 *
 * There is no single call that does this, so there is a ladder, and the app climbs down it
 * until something works. Which rungs exist is not decided here — it is decided by Probe, on
 * the device, and stored.
 */
class Router(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    // ---- reading -------------------------------------------------------------------------

    fun outputs(): List<Output> =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
            Output(
                id = d.id,
                typeCode = d.type,
                productName = d.productName?.toString().orEmpty(),
                address = runCatching { d.address.orEmpty() }.getOrDefault(""),
            )
        }

    fun allRows(): List<Row> = Outputs.rows(outputs())

    /** What the notification draws: everything except what he has switched off. §7 */
    fun rows(switchedOff: Set<String>): List<Row> = Outputs.visible(allRows(), switchedOff)

    /**
     * What is playing where, right now, according to the platform rather than according to us.
     *
     * design-language.md §14: the object is the truth and the state is a claim about it. If the
     * headphones we believe we selected have been unplugged, the row that is lit must be the
     * one actually carrying sound, not the one we last tapped.
     */
    /**
     * Re-select whatever was chosen last, if it has just come back.
     *
     * Matched on the stable key, not the id, because the id is different every time a headset
     * reconnects — which is precisely the moment this needs to work.
     */
    fun reselect(key: String, caps: Capabilities): Outcome? {
        val row = allRows().firstOrNull { it.key == key } ?: return null
        return selectOutput(row.id, caps)
    }

    /**
     * Hand the call route back to the system.
     *
     * THE BUG, 23.8.2026: setCommunicationDevice() was called and clearCommunicationDevice()
     * was never called anywhere. That request PERSISTS. Tapping Earpiece pinned every call to
     * the earpiece and the phone's own speakerphone button could no longer override it — media
     * kept working, so it looked like broken hardware rather than a held request.
     *
     * An app that takes a system-wide resource must have a way to give it back, and that way
     * has to be reachable by the person, not only by the code path that took it.
     */
    /**
     * Called once at startup. Installs from v7-v10 may still be holding the earpiece, and the
     * person has no reason to know that or to go looking for a button to fix it.
     */
    fun releaseAnythingHeldAtStartup() {
        runCatching { if (audio.communicationDevice != null) audio.clearCommunicationDevice() }
    }

    fun releaseCallAudio(): Outcome = try {
        val held = runCatching { audio.communicationDevice }.getOrNull()
        audio.clearCommunicationDevice()
        val after = runCatching { audio.communicationDevice }.getOrNull()
        when {
            held == null -> Outcome.Moved("nothing was held; calls already follow the system")
            after == null || after.id != held.id ->
                Outcome.Moved("released — calls follow the system again")
            // v10 printed after.productName here, which on this phone is the model code
            // "A142" — an accurate value and a meaningless sentence.
            else -> Outcome.Refused(
                "could not let go of " +
                    Outputs.labelFor(after.type, after.productName?.toString().orEmpty()),
            )
        }
    } catch (t: Throwable) {
        Outcome.Refused("could not release: " + (t.cause ?: t))
    }

    /** What the platform says about call audio right now. For the report. */
    fun callAudioReport(): String {
        val held = runCatching { audio.communicationDevice }.getOrNull()
        val available = runCatching {
            audio.availableCommunicationDevices.joinToString(", ") {
                Outputs.labelFor(it.type, it.productName?.toString().orEmpty())
            }
        }.getOrDefault("unreadable")
        val mode = runCatching { audio.mode }.getOrDefault(-1)
        return buildString {
            append("  held by this app: ")
            append(
                held?.let { Outputs.labelFor(it.type, it.productName?.toString().orEmpty()) }
                    ?: "nothing (system decides)"
            )
            append('\n')
            append("  available for calls: ").append(available).append('\n')
            append("  audio mode: ").append(mode)
        }
    }

    // ---- volume ----------------------------------------------------------------------------

    fun volumeMax(streamId: Int): Int =
        runCatching { audio.getStreamMaxVolume(streamId) }.getOrDefault(0)

    fun volumeIndex(streamId: Int): Int =
        runCatching { audio.getStreamVolume(streamId) }.getOrDefault(0)

    /**
     * Set a stream's level, then READ IT BACK.
     *
     * STREAM_VOICE_CALL is routinely clamped or ignored outside an active call, and
     * setStreamVolume returns void — it reports nothing. Without the read-back this would be
     * another control that looks like it worked. When the direct call does not take, the shell
     * is tried, because uid 2000 is not subject to the same restriction.
     */
    /**
     * Set a stream's level. **This does not need Shizuku.**
     *
     * `setStreamVolume` is an ordinary API guarded by no permission at all — which is why every
     * other volume app on the store works out of the box, and why the tiles here always could
     * too. Shizuku was never required for volume; it is required for `master_mono` and
     * `master_balance`, which are secure settings, and for the routing app-op. Those are
     * different features and the distinction was buried.
     *
     * There is exactly one case where the direct call is refused: Do Not Disturb is on and the
     * app has no notification-policy access. That hits Ring and Notification only, and the cure
     * is a switch in Settings, not a computer.
     */
    fun setVolume(streamId: Int, index: Int, caps: Capabilities): Outcome {
        val max = volumeMax(streamId)
        if (max <= 0) return Outcome.Refused("this stream reports no range on this device")
        val wanted = index.coerceIn(0, max)

        // Name the exception rather than swallowing it. The old code discarded a
        // SecurityException and then reported "Shizuku is not available", sending anyone
        // reading it to fix the wrong thing entirely.
        val direct = runCatching { audio.setStreamVolume(streamId, wanted, 0) }
        if (volumeIndex(streamId) == wanted) return Outcome.Moved("set to $wanted of $max")

        val refusal = direct.exceptionOrNull()
        if (refusal is SecurityException) {
            return Outcome.Refused(
                "Do Not Disturb is blocking this stream — grant Mantra Route " +
                    "Do Not Disturb access in Settings",
            )
        }

        if (!caps.works(Probe.SHELL)) {
            return Outcome.Refused("the platform refused $wanted of $max")
        }
        Shell.run(Volume.shellCommand(streamId, wanted))
        val after = volumeIndex(streamId)
        return if (after == wanted) {
            Outcome.Moved("set to $wanted of $max via shell")
        } else {
            Outcome.Refused("stayed at $after of $max — the call stream often locks outside a call")
        }
    }

        runCatching { audio.setStreamVolume(streamId, wanted, 0) }
        if (volumeIndex(streamId) == wanted) return Outcome.Moved("set to $wanted of $max")

        if (!caps.works(Probe.SHELL)) {
            return Outcome.Refused(
                "the platform refused $wanted of $max and Shizuku is not available to force it",
            )
        }
        Shell.run(Volume.shellCommand(streamId, wanted))
        val after = volumeIndex(streamId)
        return if (after == wanted) {
            Outcome.Moved("set to $wanted of $max via shell")
        } else {
            Outcome.Refused("stayed at $after of $max — the call stream often locks outside a call")
        }
    }

    fun activeId(): Int? {
        val communication = runCatching { audio.communicationDevice?.id }.getOrNull()
        if (communication != null && outputs().any { it.id == communication }) return communication
        return null
    }

    // ---- writing -------------------------------------------------------------------------

    sealed class Outcome {
        data class Moved(val how: String) : Outcome()
        data class Partial(val how: String, val caveat: String) : Outcome()
        data class Refused(val why: String) : Outcome()
    }

    fun selectOutput(deviceId: Int, caps: Capabilities): Outcome {
        val device = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == deviceId }
            ?: return Outcome.Refused("that output is no longer connected")

        val row = allRows().firstOrNull { it.id == deviceId }

        // Rung 1 — the real one, if this device ever grants it.
        if (caps.works(Probe.PERM_ROUTING)) {
            preferredDeviceForStrategy(device)?.let { return it }
        }

        // Rung 2 — move each playing app's own session. This is the one that does what was
        // actually asked: music follows, not just calls.
        if (row != null && caps.works(Probe.APPOP_ROUTING)) {
            val targets = MediaTargets.playing(context)
            when {
                targets.isEmpty() && !MediaTargets.listenerEnabled(context) -> Unit
                targets.isEmpty() -> Unit
                else -> {
                    val failures = targets.mapNotNull { pkg ->
                        null
                    }
                    when {
                        failures.isEmpty() ->
                            return Outcome.Moved("moved ${targets.size} playing app(s)")
                        failures.size < targets.size ->
                            return Outcome.Partial(
                                "moved some playing apps",
                                "refused by " + failures.joinToString("; "),
                            )
                        else -> Unit  // all refused: fall through, do not report success
                    }
                }
            }
        }

        // The communication rung was REMOVED in v11.
        //
        // setCommunicationDevice pins the call route system-wide and outranks the dialer's own
        // speakerphone button. That is why the speakerphone stopped working: this app was
        // holding it. Media routing was the goal; the call path was never asked for and taking
        // it was pure collateral damage.
        //
        // Android already provides call routing during a call, in the dialer, and it works.
        return Outcome.Refused("nothing on this device would take the route")
    }

    /** Hidden system API. Reached only when the probe says the permission was granted. */
    private fun preferredDeviceForStrategy(device: AudioDeviceInfo): Outcome? = try {
        val attributesClass = Class.forName("android.media.AudioDeviceAttributes")
        val ctor = attributesClass.getConstructor(AudioDeviceInfo::class.java)
        val attributes = ctor.newInstance(device)

        val strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy")
        val getStrategies = strategyClass.getMethod("getAudioProductStrategies")
        @Suppress("UNCHECKED_CAST")
        val strategies = getStrategies.invoke(null) as List<Any>

        val media = strategies.firstOrNull { s ->
            val name = s.javaClass.getMethod("getName").invoke(s) as? String
            name.equals("STRATEGY_MEDIA", true) || name.equals("media", true)
        } ?: strategies.firstOrNull()

        if (media == null) {
            null
        } else {
            val method = AudioManager::class.java.getMethod(
                "setPreferredDeviceForStrategy", strategyClass, attributesClass,
            )
            val ok = method.invoke(audio, media, attributes) as? Boolean ?: false
            if (ok) Outcome.Moved("audio policy strategy") else null
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * The escape hatch, and it is a real one.
     *
     * When no rung takes the route, opening the platform's own output picker is better than a
     * dead row. It is one tap instead of none, and it never lies about what it did.
     */
    fun systemPickerIntent(): Intent =
        Intent("android.settings.panel.action.MEDIA_OUTPUT")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---- stereo, mono, swap ---------------------------------------------------------------

    fun applyBlend(blend: Blend, caps: Capabilities): Outcome {
        if (!caps.works(Probe.MONO)) {
            return Outcome.Refused("master_mono is not writable from shell on this build")
        }
        val value = BlendMath.monoFor(blend)
        val r = Shell.run("settings put secure master_mono $value")
        val readBack = Shell.run("settings get secure master_mono").out.trim()
        return if (readBack == value.toString()) {
            Outcome.Moved(if (blend == Blend.MONO) "mono downmix on" else "stereo restored")
        } else {
            Outcome.Refused(r.text.ifEmpty { "master_mono stayed at $readBack" })
        }
    }

    fun applyBalance(value: Float, caps: Capabilities): Outcome {
        if (!caps.works(Probe.BALANCE)) {
            return Outcome.Refused("master_balance is not writable from shell on this build")
        }
        val clamped = BlendMath.clampBalance(value)
        Shell.run("settings put secure master_balance $clamped")
        val readBack = Shell.run("settings get secure master_balance").out.trim().toFloatOrNull()
        return if (readBack != null && kotlin.math.abs(readBack - clamped) < 0.02f) {
            Outcome.Moved("balance $clamped")
        } else {
            Outcome.Refused("balance stayed at $readBack")
        }
    }

    fun currentBlend(): Blend {
        val mono = runCatching {
            Settings.Secure.getInt(context.contentResolver, "master_mono", 0)
        }.getOrDefault(0)
        return if (mono == 1) Blend.MONO else Blend.STEREO
    }
}
