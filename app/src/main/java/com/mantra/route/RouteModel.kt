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
 * Four preset levels, cycled by one tile.
 *
 * v21 replaced the two-tile arrangement. Two tiles per channel meant ten tiles, a range printed
 * on each face to tell them apart, and two things to find in the picker for one job. One tile
 * that steps through 25, 50, 75, 100 and wraps does the same work with a fifth of the furniture
 * and no range to read.
 */
object Presets {

    val LEVELS = listOf(25, 50, 75, 100)

    /**
     * The next level up, wrapping at the top.
     *
     * Strictly greater than where we are, so a level set from somewhere else lands on the next
     * preset above it rather than jumping to the start. 63 goes to 75, not to 25.
     */
    fun next(percent: Int): Int = LEVELS.firstOrNull { it > percent } ?: LEVELS.first()

    /**
     * The next STEP to set, chosen among the steps this stream can actually reach.
     *
     * Found 25.8.2026 by driving the cycle through the real index round-trip: a stream with a
     * very coarse range GOT STUCK ON ONE LEVEL, and every press looked like it worked. On a
     * two-step stream, 25% and 50% both land on the same step, that step reads back as one
     * percentage, and `next()` on that percentage returns the level we are already at — for
     * ever.
     *
     * Cycling on percentages assumed the stream could represent them. Cycling on the steps
     * themselves cannot make that mistake: the stops are computed from the stream's own range
     * and de-duplicated, so every press moves to a different step. **Every press changes
     * something, which is the only promise a toggle has to keep.**
     *
     * A guard for "the presets collapse to a single step" was written here and then removed:
     * checked exhaustively over ranges 1 to 4096, it never happens, because 25% of any range
     * always rounds below 100% of it. Unreachable code that looks defensive is worse than none
     * — it is a branch nobody can test and everybody trusts.
     *
     * His phone reports 15 and 16 and was never affected. This is a hole found by looking, not
     * a bug reported.
     */
    fun nextIndex(currentIndex: Int, max: Int): Int {
        if (max <= 0) return 0
        val stops = LEVELS.map { Volume.indexFor(it, max) }.distinct().sorted()
        return stops.firstOrNull { it > currentIndex } ?: stops.first()
    }

    /**
     * The level to SHOW, which is not always the level measured.
     *
     * The Call stream has fifteen steps where the others have sixteen, so 25% lands on step 4
     * which is 26.7%. Within half a step of a preset, the preset is what the tile aimed at and
     * what it should say; beyond that the level came from elsewhere and is reported as it is.
     */
    fun snap(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        // Half a step, but NEVER more than half the gap between two presets. Without the cap
        // the tolerance on a coarse stream grows past 25 points and every level snaps to the
        // same preset, which is half of how the cycle got stuck.
        val halfStep = Math.round(50.0 / max).toInt().coerceIn(1, 12)
        return LEVELS.firstOrNull { Math.abs(percent - it) <= halfStep } ?: percent
    }

    /**
     * One character for the level, because a whole line has to hold two letters AND a number.
     *
     * 25 is "2", 50 is "5", 75 is "7", and 100 is "1" — the leading digit in every case.
     *
     * "1" therefore means 100 and could also mean 10-something. That collision is real and is
     * resolved by COLOUR: the tile is only ever white at 100, so a dark "1" is a tenth and a
     * white "1" is full. It is the one thing colour is spent on here.
     */
    fun digit(percent: Int): String = when {
        percent >= 100 -> "1"
        percent >= 10 -> (percent / 10).toString()
        else -> "0"
    }

    /** Full only at the top. The single state the tile paints. */
    fun isFull(percent: Int): Boolean = percent >= 100
}

object TileText {

    /**
     * TWO letters, not four.
     *
     * The face is one line now and the level's digit has to share it, so the name gets two
     * characters and the digit gets one. Two is enough to tell five channels apart when they
     * are the only five there are.
     */
    private val TWO = mapOf(
        "Call" to "CA",
        "Media" to "ME",
        "Ring" to "RI",
        "Notification" to "NO",
        "Alarm" to "AL",
    )

    fun two(name: String): String =
        TWO[name] ?: name.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "--" }

    /** The whole face: two letters and one digit, on one line. */
    fun face(name: String, percent: Int, max: Int): String =
        if (max <= 0) two(name) + "-" else two(name) + Presets.digit(Presets.snap(percent, max))

    /** The label, where a panel shows one. */
    fun label(name: String, percent: Int, max: Int): String =
        if (max <= 0) "$name unavailable" else "$name ${Presets.snap(percent, max)}%"

    /**
     * The sentence the bubble says on every press.
     *
     * A whole sentence, not a number: the tile is small and pressed without looking, and
     * "Call 100%" tells you what you touched as well as what happened. "100%" alone would not.
     */
    fun spoken(name: String, percent: Int, max: Int): String =
        if (max <= 0) "$name has no volume range on this phone"
        else "$name ${Presets.snap(percent, max)}%"
}

/** A control that goes blank on press is the bug this exists to prevent. */
object Feedback {
    const val HOLD_MS = 2200L
    fun resultLabel(message: String, fallback: String = "Done"): String =
        message.trim().ifEmpty { fallback }
}
