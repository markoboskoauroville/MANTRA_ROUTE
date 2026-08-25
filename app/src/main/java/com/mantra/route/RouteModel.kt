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
     * The level to SHOW, which is not always the level measured.
     *
     * The Call stream has fifteen steps where the others have sixteen, so 25% lands on step 4
     * which is 26.7%. Within half a step of a preset, the preset is what the tile aimed at and
     * what it should say; beyond that the level came from elsewhere and is reported as it is.
     */
    fun snap(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        val halfStep = Math.round(50.0 / max).toInt().coerceAtLeast(1)
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

/**
 * The VU meter's numbers, ported from TTT Mini's `MaVuView`.
 *
 * Ported, not re-invented: the dB conversion, the floor, the asymmetric smoothing, the peak
 * decay and the three colours are his, unchanged. Two meters that merely look similar would
 * disagree about how loud something is; this one reads the same way his keyboard's does.
 */
object Meter {

    const val FLOOR_DB = -54f

    /** Sand, for the peak mark. */
    const val PEAK_INK = 0xFFF2DDB4.toInt()

    /** The track the bar runs in. */
    const val TRACK = 0xFF2A2A2E.toInt()

    fun toDb(level: Float): Float {
        val v = kotlin.math.abs(level)
        if (v <= 0.0005f) return FLOOR_DB
        return (20.0 * kotlin.math.log10(v.toDouble())).toFloat().coerceIn(FLOOR_DB, 0f)
    }

    fun norm(db: Float): Float = ((db - FLOOR_DB) / (0f - FLOOR_DB)).coerceIn(0f, 1f)

    /** Green while there is headroom, amber approaching, red at the top. */
    fun colourFor(db: Float): Int = when {
        db > -3f -> 0xFF9B3B33.toInt()
        db > -12f -> 0xFFF0883E.toInt()
        else -> 0xFF56D364.toInt()
    }

    /**
     * Fast to rise, slow to fall.
     *
     * An attack that lags makes the meter feel dead; a release that snaps makes it feel nervous.
     * The same asymmetry every hardware meter has.
     */
    fun smooth(previous: Float, now: Float): Float =
        if (now > previous) now else previous + (now - previous) * 0.3f

    /** The peak mark falls slowly enough to read and fast enough to follow a phrase. */
    fun decayPeak(previous: Float, now: Float): Float =
        if (now > previous) now else (previous - 0.6f).coerceAtLeast(FLOOR_DB)
}

/**
 * Normalise: measure how far the loudest moment falls short of full scale, and make up the
 * difference with gain.
 *
 * Gain is expressed in MILLIBELS because that is what `LoudnessEnhancer` takes: 100 mB is 1 dB.
 *
 * The target is a shade under full scale rather than at it. Digital audio clips hard, and a peak
 * measured over a window is not the highest sample that will ever arrive — leaving a decibel of
 * room costs nothing audible and is the difference between loud and broken.
 */
object Normalize {

    const val TARGET_DB = -1f

    /** Twenty decibels. Beyond that the noise floor arrives before the music does. */
    const val MAX_GAIN_MB = 2000

    /** Below this there is nothing playing to measure, and a gain computed from silence is noise. */
    const val SILENCE_DB = -50f

    fun hasSignal(peakDb: Float): Boolean = peakDb > SILENCE_DB

    /**
     * The gain to apply, in millibels. Zero when there is nothing to measure or nothing to gain,
     * so a normalise on silence is a no-op rather than a twenty decibel surprise.
     */
    fun gainMb(peakDb: Float): Int {
        if (!hasSignal(peakDb)) return 0
        val needed = TARGET_DB - peakDb
        if (needed <= 0f) return 0
        return (needed * 100f).toInt().coerceIn(0, MAX_GAIN_MB)
    }

    /** What the button says afterwards, in decibels, because millibels mean nothing to a reader. */
    fun describe(gainMb: Int): String = when {
        gainMb <= 0 -> "already at full level"
        else -> "boosting " + String.format("%.1f", gainMb / 100f) + " dB"
    }
}
