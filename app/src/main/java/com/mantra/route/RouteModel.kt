package com.mantra.route

/**
 * The mechanism, alone.
 *
 * Nothing in this file imports an Android type, so all of it runs in a plain JVM unit test.
 *
 * v18 cut this file down from twelve objects to five. Outputs, the patch bay, the stereo blend,
 * the shell text parser and the capability model all went with Shizuku and the notification.
 * What is left is what the tiles actually use.
 */

/** One volume stream, as the platform numbers them. */
data class Stream(val id: Int, val label: String)

object Volume {

    /** The five the system volume panel shows, named as it names them. */
    val STREAMS = listOf(
        Stream(0, "Call"),
        Stream(3, "Media"),
        Stream(2, "Ring"),
        Stream(5, "Notification"),
        Stream(4, "Alarm"),
    )

    /**
     * Look a stream up by its platform id.
     *
     * The tiles used to take STREAMS[0], STREAMS[1] and so on. Reordering the list would have
     * silently swapped the Call and Media tiles, with nothing failing and no way to notice
     * except by pressing one.
     */
    fun byId(streamId: Int): Stream =
        STREAMS.firstOrNull { it.id == streamId } ?: error("no stream with id $streamId")

    /**
     * Index from a percentage. Rounds rather than truncates: on a 5-step stream, truncation
     * puts 99% at step 4 of 5 and the top of the range is unreachable.
     */
    fun indexFor(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        return Math.round(percent.coerceIn(0, 100) * max / 100.0).toInt().coerceIn(0, max)
    }

    fun percentFor(index: Int, max: Int): Int {
        if (max <= 0) return 0
        return Math.round(index.coerceIn(0, max) * 100.0 / max).toInt()
    }

    fun label(name: String, index: Int, max: Int): String =
        if (max <= 0) "$name  unavailable" else "$name  $index / $max"

    /** A stream low enough to sound broken. Named as a LEVEL so it does not read as a fault. */
    fun isLow(index: Int, max: Int): Boolean = max > 0 && percentFor(index, max) <= 25
}

/**
 * The two levels a tile moves between.
 *
 * The switching point is the MIDPOINT of the pair, not the top of it. `if (index == high) low
 * else high` discards a level sitting between the two — a stream at 87% would be raised to 100%
 * instead of halved. Midpoint means two presses always return you to where you started.
 */
data class TogglePair(val high: Int, val low: Int) {
    init { require(high > low) { "high must exceed low: $high, $low" } }
    val midpoint: Int get() = (high + low) / 2
}

object VolumeToggle {

    val LOUD = TogglePair(high = 100, low = 50)
    val QUIET = TogglePair(high = 50, low = 25)

    fun atHigh(index: Int, max: Int, pair: TogglePair): Boolean =
        max > 0 && Volume.percentFor(index, max) >= pair.midpoint

    fun target(index: Int, max: Int, pair: TogglePair): Int =
        if (atHigh(index, max, pair)) Volume.indexFor(pair.low, max)
        else Volume.indexFor(pair.high, max)

    /** The number drawn in the middle of the tile. No percent sign; the size is worth more. */
    fun face(index: Int, max: Int): String =
        if (max <= 0) "--" else Volume.percentFor(index, max).toString()
}

object TileText {

    /**
     * Four letters, because that is what fits under a number on a tile.
     *
     * Chosen by hand rather than truncated: "ALARM".take(4) gives "ALAR", which reads as
     * nothing. A short list of real names beats a clever rule.
     */
    private val FOUR = mapOf(
        "Call" to "CALL",
        "Media" to "MEDI",
        "Ring" to "RING",
        "Notification" to "NOTF",
        "Alarm" to "ALRM",
    )

    fun four(name: String): String =
        FOUR[name] ?: name.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "----" }

    fun badge(index: Int, max: Int): String = VolumeToggle.face(index, max)

    fun label(name: String, index: Int, max: Int, pair: TogglePair): String =
        if (max <= 0) "$name unavailable"
        else "$name ${Volume.percentFor(index, max)}%  (${pair.high}/${pair.low})"

    /** A control says what the NEXT press does. */
    fun nextAction(index: Int, max: Int, pair: TogglePair): String = when {
        max <= 0 -> "no range on this device"
        VolumeToggle.atHigh(index, max, pair) -> "tap for ${pair.low}%"
        else -> "tap for ${pair.high}%"
    }
}

/**
 * What this app needs, now that Shizuku is gone.
 *
 * Kept as a model rather than deleted with the probe, because the answer is the whole point:
 * every combination returns NOTHING or a switch in Settings, and a test asserts that nothing
 * can ever return SHIZUKU again.
 */
enum class Need { NOTHING, SETTINGS_TOGGLE }

object Needs {

    fun forVolume(streamId: Int, dndActive: Boolean, policyGranted: Boolean): Need = when {
        streamId != 2 && streamId != 5 -> Need.NOTHING
        !dndActive -> Need.NOTHING
        policyGranted -> Need.NOTHING
        else -> Need.SETTINGS_TOGGLE
    }

    fun describe(need: Need): String = when (need) {
        Need.NOTHING -> "works out of the box"
        Need.SETTINGS_TOGGLE -> "needs Do Not Disturb access, granted in Settings"
    }
}

/** A control that goes blank on press is the bug this exists to prevent. */
object Feedback {
    const val HOLD_MS = 2200L
    fun resultLabel(message: String, fallback: String = "Done"): String =
        message.trim().ifEmpty { fallback }
}
