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

    fun rows(): List<Row> = Outputs.rows(outputs())

    /**
     * What is playing where, right now, according to the platform rather than according to us.
     *
     * design-language.md §14: the object is the truth and the state is a claim about it. If the
     * headphones we believe we selected have been unplugged, the row that is lit must be the
     * one actually carrying sound, not the one we last tapped.
     */
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

        // Rung 1 — the real one, if this device ever grants it.
        if (caps.works(Probe.PERM_ROUTING)) {
            preferredDeviceForStrategy(device)?.let { return it }
        }

        // Rung 2 — communication routing. Public API, no privilege, but it governs the call
        // path rather than the media path. It is honest about that rather than claiming more.
        val set = runCatching { audio.setCommunicationDevice(device) }.getOrDefault(false)
        if (set) {
            return Outcome.Partial(
                "communication routing",
                "calls and voice apps follow; music may not until a stronger rung is available",
            )
        }

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
        if (blend == Blend.SWAPPED) {
            return Outcome.Refused(
                "no setting on this build exchanges the channels; balance can pan but not swap",
            )
        }
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
