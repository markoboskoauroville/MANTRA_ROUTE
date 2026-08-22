package com.mantra.route

/**
 * What this device actually allows.
 *
 * Android does not let an ordinary app move another app's media audio, and the exact set of
 * things the *shell* user may do differs by version and by vendor. This file does not assume
 * an answer. It asks the phone, one real call per capability, and records what came back —
 * which is modules/four-tests.md TEST 2 run where the truth is, rather than reasoned about
 * somewhere that has no phone.
 *
 * Every probe restores whatever it changed. A probe that leaves the device in the state it
 * used for testing is sabotage that was never undone.
 */

enum class Verdict { UNTESTED, WORKS, REFUSED, ABSENT, FAULT }

data class ProbeResult(
    val id: String,
    val title: String,
    val buys: String,
    val verdict: Verdict,
    val detail: String,
)

data class Capabilities(val results: List<ProbeResult>) {
    fun verdict(id: String): Verdict =
        results.firstOrNull { it.id == id }?.verdict ?: Verdict.UNTESTED

    fun works(id: String): Boolean = verdict(id) == Verdict.WORKS

    val anyRouting: Boolean get() = works(Probe.PERM_ROUTING) || works(Probe.APPOP_ROUTING)
}

object Probe {

    const val SHELL = "shell"
    const val MONO = "mono"
    const val BALANCE = "balance"
    const val SWAP = "swap"
    const val PERM_ROUTING = "perm_routing"
    const val APPOP_ROUTING = "appop_routing"
    const val CMD_AUDIO = "cmd_audio"
    const val CMD_MEDIA_ROUTER = "cmd_media_router"
    const val CMD_BLUETOOTH = "cmd_bluetooth"
    const val LISTENER = "listener"

    /** The whole list, always in this order, always all of them. */
    fun runAll(packageName: String): Capabilities = listOf(
        shell(),
        mono(),
        balance(),
        swap(),
        permRouting(packageName),
        appopRouting(packageName),
        listener(packageName),
        cmd(CMD_AUDIO, "cmd audio", "reading and setting audio policy from shell", "audio"),
        cmd(CMD_MEDIA_ROUTER, "cmd media_router", "transferring a route between devices", "media_router"),
        cmd(CMD_BLUETOOTH, "cmd bluetooth_manager", "making a paired device the active one", "bluetooth_manager"),
    ).let { Capabilities(it) }

    /**
     * The same list, titles only, with no shell call and no side effect.
     *
     * The screen needs this to draw every probe row before anything has been probed. §1: the
     * rows must exist from the first frame, holding "not tried", rather than appearing one by
     * one as results arrive.
     */
    fun runAllTitlesOnly(): List<Triple<String, String, String>> = listOf(
        Triple(SHELL, "Shizuku shell", "everything below"),
        Triple(MONO, "Mono downmix", "collapsing both channels into one, everywhere"),
        Triple(BALANCE, "Left / right balance", "panning the whole system towards one ear"),
        Triple(SWAP, "Swap L / R", "exchanging the two channels"),
        Triple(PERM_ROUTING, "MODIFY_AUDIO_ROUTING", "moving any app's media audio, directly"),
        Triple(APPOP_ROUTING, "MEDIA_ROUTING_CONTROL app-op", "moving another app's audio through MediaRouter2"),
        Triple(LISTENER, "Notification listener", "naming which app is currently playing"),
        Triple(CMD_AUDIO, "cmd audio", "reading and setting audio policy from shell"),
        Triple(CMD_MEDIA_ROUTER, "cmd media_router", "transferring a route between devices"),
        Triple(CMD_BLUETOOTH, "cmd bluetooth_manager", "making a paired device the active one"),
    )

    private fun shell(): ProbeResult {
        if (!Shell.isRunning()) {
            return ProbeResult(
                SHELL, "Shizuku shell", "everything below",
                Verdict.ABSENT, Shell.NOT_RUNNING,
            )
        }
        if (!Shell.hasPermission()) {
            return ProbeResult(
                SHELL, "Shizuku shell", "everything below",
                Verdict.REFUSED, Shell.NO_PERMISSION,
            )
        }
        val r = Shell.run("id -u")
        val uid = r.out.trim()
        return when {
            !r.ok -> ProbeResult(SHELL, "Shizuku shell", "everything below", Verdict.FAULT, r.text)
            uid == "2000" -> ProbeResult(SHELL, "Shizuku shell", "everything below", Verdict.WORKS, "running as shell, uid 2000")
            uid == "0" -> ProbeResult(SHELL, "Shizuku shell", "everything below", Verdict.WORKS, "running as root, uid 0")
            else -> ProbeResult(SHELL, "Shizuku shell", "everything below", Verdict.FAULT, "unexpected uid: $uid")
        }
    }

    /**
     * Read a secure setting, write a different value, read it back, put the old one back.
     *
     * Reading back is the point. `settings put` exits 0 whether or not the write was allowed
     * on some builds, so a zero exit code is not evidence — the value is.
     */
    private fun secureSettingWritable(
        id: String,
        title: String,
        buys: String,
        key: String,
        testValue: String,
        matches: (String) -> Boolean,
    ): ProbeResult {
        if (!Shell.hasPermission() || !Shell.isRunning()) {
            return ProbeResult(id, title, buys, Verdict.UNTESTED, "no shell")
        }
        val before = Shell.run("settings get secure $key").out.trim()
        val put = Shell.run("settings put secure $key $testValue")
        if (!put.ok && put.err.isNotEmpty()) {
            return ProbeResult(id, title, buys, Verdict.REFUSED, put.text)
        }
        val after = Shell.run("settings get secure $key").out.trim()

        // Put it back exactly as found. "null" is what settings prints for an unset key.
        if (before == "null" || before.isEmpty()) {
            Shell.run("settings delete secure $key")
        } else {
            Shell.run("settings put secure $key $before")
        }

        return if (matches(after)) {
            ProbeResult(id, title, buys, Verdict.WORKS, "$key accepted $testValue, restored to $before")
        } else {
            ProbeResult(id, title, buys, Verdict.REFUSED, "$key stayed at '$after' after writing $testValue")
        }
    }

    private fun mono() = secureSettingWritable(
        MONO, "Mono downmix", "collapsing both channels into one, everywhere",
        "master_mono", "1",
    ) { it.trim() == "1" }

    private fun balance() = secureSettingWritable(
        BALANCE, "Left / right balance", "panning the whole system towards one ear",
        "master_balance", "0.5",
    ) { it.trim().toFloatOrNull()?.let { v -> kotlin.math.abs(v - 0.5f) < 0.01f } == true }

    /**
     * The honest one.
     *
     * master_balance pans; it does not exchange the channels. Nothing in secure settings does.
     * A real swap would mean rewriting somebody else's PCM, which no non-system app can do.
     * This probe exists so the answer is measured on the device and printed, rather than the
     * control shipping as a switch that quietly does nothing.
     */
    private fun swap(): ProbeResult {
        if (!Shell.hasPermission() || !Shell.isRunning()) {
            return ProbeResult(SWAP, "Swap L / R", "exchanging the two channels", Verdict.UNTESTED, "no shell")
        }
        val listing = Shell.run("settings list secure")
        val candidates = listing.out.lineSequence()
            .map { it.substringBefore('=') }
            .filter { it.contains("swap", true) || it.contains("channel", true) }
            .toList()
        return if (candidates.isEmpty()) {
            ProbeResult(
                SWAP, "Swap L / R", "exchanging the two channels",
                Verdict.ABSENT,
                "no secure setting on this build exchanges channels; balance pans but cannot swap",
            )
        } else {
            ProbeResult(
                SWAP, "Swap L / R", "exchanging the two channels",
                Verdict.UNTESTED,
                "candidates found, not yet tried: " + candidates.joinToString(", "),
            )
        }
    }

    /**
     * The long shot, tried once so it never has to be wondered about again.
     *
     * MODIFY_AUDIO_ROUTING is signature|privileged. `pm grant` only moves permissions whose
     * protection level allows it, so this is expected to be refused — but the exact refusal is
     * worth having in writing, and if a vendor build ever says yes, everything else here
     * becomes unnecessary.
     */
    private fun permRouting(packageName: String): ProbeResult {
        val id = PERM_ROUTING
        val title = "MODIFY_AUDIO_ROUTING"
        val buys = "moving any app's media audio, directly"
        if (!Shell.hasPermission() || !Shell.isRunning()) {
            return ProbeResult(id, title, buys, Verdict.UNTESTED, "no shell")
        }
        Shell.run("pm grant $packageName android.permission.MODIFY_AUDIO_ROUTING")
        val check = Shell.run(
            "dumpsys package $packageName | grep -i MODIFY_AUDIO_ROUTING"
        )
        val granted = check.out.contains("granted=true", ignoreCase = true)
        return if (granted) {
            ProbeResult(id, title, buys, Verdict.WORKS, "granted")
        } else {
            ProbeResult(
                id, title, buys, Verdict.REFUSED,
                "not grantable, as expected for a signature|privileged permission",
            )
        }
    }

    /**
     * The one that might actually open the door.
     *
     * Android 15 added MEDIA_ROUTING_CONTROL so a companion app can move audio belonging to a
     * different app. If its app-op can be set from shell, the proxy MediaRouter2 path becomes
     * available without pretending to be a watch.
     */
    private fun appopRouting(packageName: String): ProbeResult {
        val id = APPOP_ROUTING
        val title = "MEDIA_ROUTING_CONTROL app-op"
        val buys = "moving another app's audio through MediaRouter2"
        if (!Shell.hasPermission() || !Shell.isRunning()) {
            return ProbeResult(id, title, buys, Verdict.UNTESTED, "no shell")
        }
        val set = Shell.run("appops set $packageName MEDIA_ROUTING_CONTROL allow")
        val read = Shell.run("appops get $packageName MEDIA_ROUTING_CONTROL")
        val allowed = read.out.contains("allow", ignoreCase = true)
        return when {
            allowed -> ProbeResult(id, title, buys, Verdict.WORKS, read.out.trim())
            read.text.contains("no operations", true) || set.text.contains("Unknown operation", true) ->
                ProbeResult(id, title, buys, Verdict.ABSENT, "this build does not know the op")
            else -> ProbeResult(id, title, buys, Verdict.REFUSED, (set.text + " " + read.text).trim())
        }
    }

    /**
     * Being allowed to see the media sessions.
     *
     * Without this the proxy router has no package to aim at, so it is useless on its own and
     * essential alongside the app-op. `cmd notification allow_listener` is the shell path;
     * the alternative is sending him to a settings screen and hoping.
     */
    private fun listener(packageName: String): ProbeResult {
        val id = LISTENER
        val title = "Notification listener"
        val buys = "naming which app is currently playing"
        if (!Shell.hasPermission() || !Shell.isRunning()) {
            return ProbeResult(id, title, buys, Verdict.UNTESTED, "no shell")
        }
        val component = "$packageName/$packageName.RouteListener"
        Shell.run("cmd notification allow_listener $component")
        val enabled = Shell.run("settings get secure enabled_notification_listeners").out
        return if (enabled.contains(packageName)) {
            ProbeResult(id, title, buys, Verdict.WORKS, "listener allowed")
        } else {
            ProbeResult(
                id, title, buys, Verdict.REFUSED,
                "not in enabled_notification_listeners after allow_listener",
            )
        }
    }

    /** Does a shell command service exist at all on this build? */
    private fun cmd(id: String, title: String, buys: String, service: String): ProbeResult {
        if (!Shell.hasPermission() || !Shell.isRunning()) {
            return ProbeResult(id, title, buys, Verdict.UNTESTED, "no shell")
        }
        val list = Shell.run("cmd -l")
        val present = list.out.lineSequence().any { it.trim() == service }
        if (!present) return ProbeResult(id, title, buys, Verdict.ABSENT, "no such service in `cmd -l`")
        val help = Shell.run("cmd $service help")
        return ProbeResult(
            id, title, buys, Verdict.WORKS,
            help.text.lineSequence().take(6).joinToString("\n").ifEmpty { "service present" },
        )
    }
}
