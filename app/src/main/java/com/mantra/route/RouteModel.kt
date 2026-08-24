package com.mantra.route

/**
 * The mechanism, alone.
 *
 * Nothing in this file imports an Android type. That is deliberate: modules/four-tests.md
 * TEST 1 says if you cannot run the logic without starting the whole application, the logic
 * is tangled into the plumbing. Everything here runs in a plain JVM unit test.
 */

/** One raw output as the platform reports it, flattened to primitives. */
data class Output(
    val id: Int,
    val typeCode: Int,
    val productName: String,
    val address: String,
)

enum class Glyph { SPEAKER, EARPIECE, WIRED, USB, BLUETOOTH, BLE, HEARING_AID, HDMI, DOCK }

/**
 * One row as the notification will draw it.
 *
 * [key] is what gets remembered, not [id]. A device id is handed out per connection: unplug
 * the headphones and plug them back in and the id is different, so a preference stored
 * against an id is forgotten the first time the thing it names is switched off and on. The
 * key is made of what does not change.
 */
data class Row(
    val id: Int,
    val typeCode: Int,
    val label: String,
    val glyph: Glyph,
) {
    val key: String get() = Outputs.keyOf(typeCode, label)
}

object AudioType {
    const val EARPIECE = 1
    const val SPEAKER = 2
    const val WIRED_HEADSET = 3
    const val WIRED_HEADPHONES = 4
    const val LINE_ANALOG = 5
    const val LINE_DIGITAL = 6
    const val BLUETOOTH_SCO = 7
    const val BLUETOOTH_A2DP = 8
    const val HDMI = 9
    const val HDMI_ARC = 10
    const val USB_DEVICE = 11
    const val USB_ACCESSORY = 12
    const val DOCK = 13
    const val FM = 14
    const val TELEPHONY = 18
    const val AUX_LINE = 19
    const val IP = 20
    const val BUS = 21
    const val USB_HEADSET = 22
    const val HEARING_AID = 23
    const val SPEAKER_SAFE = 24
    const val REMOTE_SUBMIX = 25
    const val BLE_HEADSET = 26
    const val BLE_SPEAKER = 27
    const val HDMI_EARC = 29
    const val BLE_BROADCAST = 30
    const val DOCK_ANALOG = 31
}

object Outputs {

    /** Types that are never a place a person chooses to listen. */
    private val HIDDEN = setOf(
        AudioType.TELEPHONY,
        AudioType.REMOTE_SUBMIX,
        AudioType.BUS,
        AudioType.IP,
        AudioType.SPEAKER_SAFE,
        AudioType.FM,
    )

    /**
     * Display order. Fixed, not by discovery order.
     *
     * design-language.md §1: nothing appears, nothing disappears. A list that reorders itself
     * when a device connects is the same failure wearing different clothes — the row you were
     * reaching for moves under your finger.
     */
    private val ORDER = listOf(
        AudioType.SPEAKER, AudioType.EARPIECE,
        AudioType.WIRED_HEADPHONES, AudioType.WIRED_HEADSET,
        AudioType.LINE_ANALOG, AudioType.LINE_DIGITAL, AudioType.AUX_LINE,
        AudioType.USB_HEADSET, AudioType.USB_DEVICE, AudioType.USB_ACCESSORY,
        AudioType.BLE_HEADSET, AudioType.BLE_SPEAKER, AudioType.BLE_BROADCAST,
        AudioType.BLUETOOTH_A2DP, AudioType.BLUETOOTH_SCO,
        AudioType.HEARING_AID,
        AudioType.HDMI, AudioType.HDMI_ARC, AudioType.HDMI_EARC,
        AudioType.DOCK, AudioType.DOCK_ANALOG,
    )

    /**
     * When one physical headset is reported three times — as BLE, as A2DP and as SCO — keep one.
     * Higher wins. SCO is the telephone-quality profile and is never the one to show.
     */
    private fun quality(typeCode: Int): Int = when (typeCode) {
        AudioType.BLE_HEADSET, AudioType.BLE_SPEAKER -> 3
        AudioType.BLUETOOTH_A2DP -> 2
        AudioType.BLUETOOTH_SCO -> 1
        else -> 0
    }

    private fun isBluetooth(typeCode: Int) = quality(typeCode) > 0 ||
        typeCode == AudioType.BLE_BROADCAST

    fun glyphFor(typeCode: Int): Glyph = when (typeCode) {
        AudioType.SPEAKER -> Glyph.SPEAKER
        AudioType.EARPIECE -> Glyph.EARPIECE
        AudioType.WIRED_HEADSET, AudioType.WIRED_HEADPHONES,
        AudioType.LINE_ANALOG, AudioType.LINE_DIGITAL, AudioType.AUX_LINE -> Glyph.WIRED
        AudioType.USB_HEADSET, AudioType.USB_DEVICE, AudioType.USB_ACCESSORY -> Glyph.USB
        AudioType.BLE_HEADSET, AudioType.BLE_SPEAKER, AudioType.BLE_BROADCAST -> Glyph.BLE
        AudioType.BLUETOOTH_A2DP, AudioType.BLUETOOTH_SCO -> Glyph.BLUETOOTH
        AudioType.HEARING_AID -> Glyph.HEARING_AID
        AudioType.HDMI, AudioType.HDMI_ARC, AudioType.HDMI_EARC -> Glyph.HDMI
        else -> Glyph.DOCK
    }

    /**
     * The name to draw.
     *
     * The platform's productName is the phone's model name for anything built in — "Nothing
     * Phone (2a)" on the earpiece and again on the speaker is two rows with the same words and
     * no way to tell them apart. So built-in outputs get a fixed name and only removable ones
     * are allowed to name themselves.
     */
    fun labelFor(typeCode: Int, productName: String): String {
        val given = productName.trim()
        return when (typeCode) {
            AudioType.SPEAKER -> "Phone speaker"
            AudioType.EARPIECE -> "Earpiece"
            AudioType.WIRED_HEADSET -> "Wired headset"
            AudioType.WIRED_HEADPHONES -> "Wired headphones"
            AudioType.LINE_ANALOG, AudioType.AUX_LINE -> "Line out"
            AudioType.LINE_DIGITAL -> "Digital line out"
            AudioType.HDMI, AudioType.HDMI_ARC, AudioType.HDMI_EARC -> "HDMI"
            AudioType.DOCK, AudioType.DOCK_ANALOG -> "Dock"
            AudioType.BLE_BROADCAST -> if (given.isEmpty()) "LE broadcast" else "$given (broadcast)"
            else -> given.ifEmpty { fallbackName(typeCode) }
        }
    }

    private fun fallbackName(typeCode: Int): String = when (typeCode) {
        AudioType.USB_HEADSET -> "USB headset"
        AudioType.USB_DEVICE, AudioType.USB_ACCESSORY -> "USB audio"
        AudioType.BLUETOOTH_A2DP, AudioType.BLUETOOTH_SCO -> "Bluetooth"
        AudioType.BLE_HEADSET, AudioType.BLE_SPEAKER -> "LE audio"
        AudioType.HEARING_AID -> "Hearing aid"
        else -> "Audio output"
    }

    /**
     * The whole rule, in one pure function: hide what is not a destination, collapse one
     * physical headset reported under several profiles, order by kind not by discovery.
     */
    fun rows(outputs: List<Output>): List<Row> {
        val visible = outputs.filter { it.typeCode !in HIDDEN }

        // Collapse Bluetooth duplicates. Key on address where there is one, because two pairs of
        // earbuds may legitimately share a product name; fall back to the name when the address
        // is empty, which it is when BLUETOOTH_CONNECT has not been granted.
        val collapsed = visible
            .groupBy { o ->
                if (isBluetooth(o.typeCode)) {
                    "bt:" + o.address.ifEmpty { o.productName.trim().lowercase() }
                } else {
                    "id:" + o.id
                }
            }
            .map { (_, group) -> group.maxByOrNull { quality(it.typeCode) }!! }

        return collapsed
            .sortedWith(
                compareBy(
                    { ORDER.indexOf(it.typeCode).let { i -> if (i < 0) ORDER.size else i } },
                    { labelFor(it.typeCode, it.productName).lowercase() },
                    { it.id },
                )
            )
            .map {
                Row(it.id, it.typeCode, labelFor(it.typeCode, it.productName), glyphFor(it.typeCode))
            }
    }

    /** Stable across reconnection, and stable across a reboot. */
    fun keyOf(typeCode: Int, label: String): String = "$typeCode:${label.trim().lowercase()}"

    /**
     * design-language.md §7: store what is switched OFF, not what is on.
     *
     * If the set held what to show, an output type added by a later Android — or a headset
     * bought next year — would be absent from a saved list that predates it and would never
     * appear. Holding the exclusions instead means anything new is live by default.
     */
    fun visible(rows: List<Row>, switchedOff: Set<String>): List<Row> =
        rows.filterNot { it.key in switchedOff }
}

/** Stereo mode, as a closed set. design-language.md §6: exactly one in force, so a radio. */
enum class Blend { STEREO, MONO, SWAPPED }

object BlendMath {
    /**
     * master_balance is a pan, not a swap: -1.0 is all left, +1.0 is all right, 0.0 is centre.
     * There is no secure setting that exchanges the channels, so SWAPPED cannot be expressed
     * here and must not pretend to be. It returns null and the caller must refuse.
     */
    fun balanceFor(blend: Blend): Float? = when (blend) {
        Blend.STEREO -> 0.0f
        Blend.MONO -> 0.0f
        Blend.SWAPPED -> null
    }

    fun monoFor(blend: Blend): Int = if (blend == Blend.MONO) 1 else 0

    fun clampBalance(value: Float): Float = value.coerceIn(-1.0f, 1.0f)
}

/**
 * Reading a shell command's exit code without asking the process for it.
 *
 * TEST 2 on the real phone, 22.8.2026: every probe came back
 * `IllegalArgumentException: process hasn't exited`.
 *
 * ShizukuRemoteProcess is not a local process. `waitFor(timeout, unit)` and `exitValue()` are
 * binder calls to the Shizuku server, and binder does not carry exception classes — it maps a
 * throwable onto a small fixed set of codes. The server's IllegalThreadStateException is a
 * *subclass* of IllegalArgumentException, so it travels as the parent and arrives as a plain
 * IllegalArgumentException. The JDK's own timed waitFor catches IllegalThreadStateException
 * and therefore does not catch it.
 *
 * So this stops asking. The command carries its own exit code out through stdout, and the
 * process is finished when its stream reaches EOF. No binder call, nothing to flatten.
 */
object ExitMarker {

    const val TOKEN = "__MR_EXIT__"

    /** `command` becomes `command` followed by a line printing its status. */
    fun wrap(command: String): String = command + "\necho " + TOKEN + "${'$'}?"

    data class Parsed(val code: Int, val output: String, val found: Boolean)

    /**
     * The marker is looked for from the END, because a command may legitimately print
     * something containing the token — `settings list secure` echoing it back, or the probe
     * that greps for its own text. The real one is always last.
     */
    fun parse(raw: String): Parsed {
        val lines = raw.split("\n")
        val index = lines.indexOfLast { it.trim().startsWith(TOKEN) }
        if (index < 0) {
            return Parsed(-1, raw.trim(), false)
        }
        val code = lines[index].trim().removePrefix(TOKEN).trim().toIntOrNull() ?: -1
        val output = lines.filterIndexed { i, _ -> i != index }.joinToString("\n").trim()
        return Parsed(code, output, true)
    }
}

/**
 * Shell text handling, pure.
 *
 * All of this used to be inline in Probe, where it could not be tested, and one piece of it was
 * wrong on the real phone: the probe restored a secure setting and then *claimed* it had,
 * without looking. The claim was false, and the next run read the stuck test value as though it
 * were the user's own setting and wrote it back as "original". Two runs and mono was permanent.
 */
object ShellText {

    private fun unset(value: String): Boolean {
        val v = value.trim()
        return v.isEmpty() || v == "null"
    }

    /** How to put [key] back to the value it held before a probe touched it. */
    fun restoreCommand(key: String, before: String): String =
        if (unset(before)) "settings delete secure $key"
        else "settings put secure $key ${before.trim()}"

    /**
     * Did the restore actually land? An unset key reads back as "null", so "null" and "" are
     * the same state and must compare equal — otherwise a correct restore reports as a failure.
     */
    fun restored(before: String, now: String): Boolean =
        if (unset(before)) unset(now) else before.trim() == now.trim()

    /**
     * A shell service that exists but has no commands is not a capability.
     *
     * `cmd media_router` answered "No shell command implementation." on the phone and the probe
     * scored it WORKS, because the probe only asked whether the service was listed. A green
     * that means nothing is worse than a red.
     */
    fun cmdUsable(help: String): Boolean {
        val h = help.trim()
        if (h.isEmpty()) return false
        if (h.contains("No shell command implementation", ignoreCase = true)) return false
        if (h.contains("Unknown command", ignoreCase = true)) return false
        return true
    }
}

/**
 * What the panel is allowed to claim.
 *
 * On the phone, 23.8.2026: "colors are changing, buttons are working, but in actual reality
 * audio is coming through whatever OS choose to do." Exactly right, and the colour was the
 * bug. The amber row was painted from the COMMUNICATION route — the call path — and when that
 * was empty it fell back to whatever the user last tapped. Either way it showed a tap, not a
 * fact.
 */
object Claim {

    /**
     * Media routing needs a privilege this phone has refused. Without it the app can move the
     * call path and nothing else, and must say so in those words.
     */
    fun canMoveMedia(routingWorks: Boolean): Boolean = routingWorks

    fun headline(shellRunning: Boolean, shellAllowed: Boolean, routingWorks: Boolean): String = when {
        !shellRunning -> "Shizuku is not running"
        !shellAllowed -> "Shizuku has not been allowed"
        routingWorks -> "Routing media"
        else -> "Calls only — media follows the system"
    }

    /**
     * An earpiece is not a place music can go. Android will route a call there and nothing
     * else, so a row offering it as a music destination is an offer that cannot be honoured.
     */
    fun carriesMedia(typeCode: Int): Boolean = when (typeCode) {
        AudioType.EARPIECE, AudioType.TELEPHONY -> false
        else -> true
    }

    /**
     * The suffix on a row. Not a colour: the palette already spends amber on "in force", and
     * inventing a second meaning for it is how a legend stops being readable.
     */
    fun annotation(carriesMedia: Boolean, holdsCallRoute: Boolean): String = when {
        !carriesMedia && holdsCallRoute -> "  calls only, in use"
        !carriesMedia -> "  calls only"
        holdsCallRoute -> "  calls here"
        else -> ""
    }
}

/**
 * Fitting the whole control set into one collapsed notification row.
 *
 * The collapsed view is capped by the platform at roughly 64dp, so the labels have to be short
 * enough that a row of them does not wrap or clip. §10 still applies: shorten by RULE and let
 * the row size itself, never by giving each chip a fixed fraction of the width.
 */
object Chip {

    /**
     * "Wired headphones" is not going to fit beside four other chips, and "Wired headphon…" is
     * worse than "Wired". Prefer a whole first word over a truncated phrase.
     */
    fun short(label: String, limit: Int = 10): String {
        val clean = label.trim()
        if (clean.length <= limit) return clean
        val firstWord = clean.substringBefore(' ')
        if (firstWord.length <= limit && firstWord.isNotEmpty()) return firstWord
        return clean.take(limit - 1).trimEnd() + "…"
    }
}

/**
 * The patch bay.
 *
 * Asked for on 23.8.2026, in the language of an X32: sources down one side, destinations across
 * the top, and a crosspoint you press to connect. It is the right model, and not only because
 * it is familiar — a crosspoint grid has somewhere to PUT the fact that a connection is
 * impossible. Buttons do not. A button either works or disappoints; a blocked crosspoint is
 * information.
 *
 * Android has two independent routing paths, and conflating them is what made this app confusing
 * for six versions. They are separate rows here because they are separate in the platform.
 */
enum class Path { MEDIA, CALL }

enum class Cell {
    /** Carrying this path right now. */
    CONNECTED,

    /** Can be patched. */
    CONNECTABLE,

    /** Cannot be patched on this phone, ever, and the grid says so rather than failing later. */
    BLOCKED,
}

object PatchBay {

    /**
     * Which outputs the platform will accept for a CALL. Deliberately broad: the phone decides,
     * and this only excludes the ones that are never offered.
     */
    fun carriesCall(typeCode: Int): Boolean = when (typeCode) {
        AudioType.HDMI, AudioType.HDMI_ARC, AudioType.HDMI_EARC,
        AudioType.DOCK, AudioType.DOCK_ANALOG,
        AudioType.TELEPHONY, AudioType.REMOTE_SUBMIX -> false
        else -> true
    }

    /**
     * The whole rule for one crosspoint.
     *
     * MEDIA to an earpiece is BLOCKED and always will be — Android does not route music there,
     * and offering it was this app's longest-standing lie. MEDIA anywhere else is blocked too
     * unless a real routing capability was measured, because without one the app cannot move
     * music no matter which cell is pressed.
     */
    fun cell(
        path: Path,
        typeCode: Int,
        routingWorks: Boolean,
        isCurrent: Boolean,
    ): Cell = when (path) {
        Path.CALL ->
            if (!carriesCall(typeCode)) Cell.BLOCKED
            else if (isCurrent) Cell.CONNECTED
            else Cell.CONNECTABLE

        Path.MEDIA ->
            if (!Claim.carriesMedia(typeCode)) Cell.BLOCKED
            else if (!routingWorks) Cell.BLOCKED
            else if (isCurrent) Cell.CONNECTED
            else Cell.CONNECTABLE
    }

    /** The mark drawn in the cell. §3: the shape carries it, colour reinforces it. */
    fun mark(cell: Cell): String = when (cell) {
        Cell.CONNECTED -> "\u25CF"     // filled dot: patched
        Cell.CONNECTABLE -> "\u25CB"   // open dot: free crosspoint
        Cell.BLOCKED -> "\u00B7"       // middle dot: no such connection
    }

    /**
     * One line explaining why a row can go nowhere, so the grid is not a wall of dots with no
     * account of itself.
     */
    fun why(path: Path, routingWorks: Boolean): String = when {
        path == Path.CALL -> "calls follow this app"
        routingWorks -> "media follows this app"
        else -> "media cannot be moved on this phone — use the system switcher"
    }
}

/**
 * Volume, per stream.
 *
 * Reported 23.8.2026: "switching works but the volume is stuck at some very low level."
 * Correct, and it exposed a hole in the whole design. Android has no per-DEVICE volume. There
 * is no "earpiece volume" and no "speaker volume". Volume is per STREAM, and the call path and
 * the media path are different streams with separate, independently remembered levels.
 *
 * So switching the call route to the earpiece hands you STREAM_VOICE_CALL's level, which may
 * not have been touched in months and sits wherever it was left. Nothing in this app ever
 * showed it, let alone let it be changed. That is the bug: the routing was visible and the
 * gain was not.
 */
data class Stream(val id: Int, val label: String)

object Volume {

    /** The four that matter here. IDs are the AudioManager STREAM_* constants. */
    val STREAMS = listOf(
        Stream(0, "Call"),
        Stream(3, "Music"),
        Stream(2, "Ring"),
        Stream(4, "Alarm"),
    )

    /**
     * Index from a slider percentage.
     *
     * Rounds rather than truncates: on a 5-step stream, truncation puts 99% at step 4 of 5 and
     * the top of the slider never reaches the top of the range.
     */
    fun indexFor(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        val clamped = percent.coerceIn(0, 100)
        return Math.round(clamped * max / 100.0).toInt().coerceIn(0, max)
    }

    fun percentFor(index: Int, max: Int): Int {
        if (max <= 0) return 0
        return Math.round(index.coerceIn(0, max) * 100.0 / max).toInt()
    }

    fun label(name: String, index: Int, max: Int): String =
        if (max <= 0) "$name  unavailable" else "$name  $index / $max"

    /**
     * Flag a stream sitting low enough to sound broken.
     *
     * This is the specific complaint: audio that is technically routed correctly and inaudible
     * in practice reads as a routing failure. Naming it as a LEVEL is the fix.
     */
    fun isLow(index: Int, max: Int): Boolean = max > 0 && percentFor(index, max) <= 25

    /** Shell fallback when setStreamVolume is clamped or refused outside a call. */
    fun shellCommand(streamId: Int, index: Int): String =
        "media volume --stream $streamId --set $index"
}

/**
 * Press feedback.
 *
 * Reported 23.8.2026: "I press and there is no interaction, I must have signal I pressed the
 * actual button. The copy button is interacting, so I know."
 *
 * Exactly right, and the copy button was an accident rather than a principle — it changed its
 * own text because it had something to say. Every control here now does the same thing on the
 * same rule: the label becomes the RESULT, holds long enough to be read, then returns to
 * saying what the next press will do.
 */
object Feedback {

    /** Long enough to read a short sentence without racing, short enough not to feel stuck. */
    const val HOLD_MS = 2200L

    /**
     * The label to show immediately after a press.
     *
     * Never empty: a control that goes blank on press is the failure being complained about.
     * If an action produced no message, say that it ran.
     */
    fun resultLabel(message: String, fallback: String = "Done"): String =
        message.trim().ifEmpty { fallback }
}
