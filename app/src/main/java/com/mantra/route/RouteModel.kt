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
