package com.mantra.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST 1 — the mechanism, alone. No Android, no device, no tile.
 *
 * Rewritten for v18. The old suite tested Outputs, the patch bay, the stereo blend and the
 * shell parser, none of which exist any more. A test kept alive for code that has gone is a
 * green light nobody is reading.
 */
class VolumeMathTest {

    @Test
    fun `the top of the range is reachable`() {
        // Truncation puts 99% at 4 of 5 and the slider never reaches maximum. Rounding fixes it.
        assertEquals(5, Volume.indexFor(100, 5))
        assertEquals(5, Volume.indexFor(99, 5))
        assertEquals(15, Volume.indexFor(100, 15))
    }

    @Test
    fun `both ends are exact`() {
        assertEquals(0, Volume.indexFor(0, 15))
        assertEquals(0, Volume.percentFor(0, 15))
        assertEquals(100, Volume.percentFor(15, 15))
    }

    @Test
    fun `a stream with no range does not divide by zero`() {
        assertEquals(0, Volume.indexFor(50, 0))
        assertEquals(0, Volume.percentFor(3, 0))
        assertEquals("Call  unavailable", Volume.label("Call", 3, 0))
        assertEquals(false, Volume.isLow(0, 0))
    }

    @Test
    fun `out of range input is clamped, not wrapped`() {
        assertEquals(15, Volume.indexFor(400, 15))
        assertEquals(0, Volume.indexFor(-50, 15))
    }

    @Test
    fun `the stuck-low case is flagged, because it reads as a routing failure`() {
        assertTrue(Volume.isLow(1, 15))
        assertEquals(false, Volume.isLow(8, 15))
        assertTrue(Volume.isLow(25, 100))
        assertEquals(false, Volume.isLow(26, 100))
    }

    @Test
    fun `streams are found by platform id, not by list position`() {
        assertEquals("Call", Volume.byId(0).label)
        assertEquals("Media", Volume.byId(3).label)
        assertEquals("Ring", Volume.byId(2).label)
        assertEquals("Notification", Volume.byId(5).label)
        assertEquals("Alarm", Volume.byId(4).label)
    }

    @Test
    fun `an unknown stream id is a fault, not a wrong answer`() {
        try {
            Volume.byId(99)
            throw AssertionError("expected a failure for an unknown stream id")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("99"))
        }
    }
}

class ToggleTest {

    @Test
    fun `a stream that is loud but not maximum still quietens`() {
        // The case the midpoint rule exists for. The naive `if (index == high)` rule raises
        // 13/15 to 100% instead of halving it, and every other case agrees with it.
        assertTrue(VolumeToggle.atHigh(13, 15, VolumeToggle.LOUD))
        assertEquals(8, VolumeToggle.target(13, 15, VolumeToggle.LOUD))
        assertEquals(false, VolumeToggle.atHigh(11, 15, VolumeToggle.LOUD))
        assertEquals(15, VolumeToggle.target(11, 15, VolumeToggle.LOUD))
    }

    @Test
    fun `two presses return you to where you started, for either pair`() {
        for (pair in listOf(VolumeToggle.LOUD, VolumeToggle.QUIET))
            for (max in listOf(5, 7, 15, 16, 30)) {
                val start = Volume.indexFor(pair.low, max)
                val once = VolumeToggle.target(start, max, pair)
                assertEquals("pair=$pair max=$max", start, VolumeToggle.target(once, max, pair))
            }
    }

    @Test
    fun `the quiet tile moves between 50 and 25`() {
        val q = VolumeToggle.QUIET
        assertEquals(8, VolumeToggle.target(4, 16, q))
        assertEquals(4, VolumeToggle.target(8, 16, q))
    }

    @Test
    fun `a loud stream pressed on the quiet tile goes quiet, not louder`() {
        // Stopping at 50 would take two presses to reach the level the tile exists for.
        assertEquals(4, VolumeToggle.target(16, 16, VolumeToggle.QUIET))
    }

    @Test
    fun `the midpoint is the switching point and differs per pair`() {
        assertEquals(75, VolumeToggle.LOUD.midpoint)
        assertEquals(37, VolumeToggle.QUIET.midpoint)
    }

    @Test
    fun `an inverted pair is rejected at construction`() {
        try {
            TogglePair(high = 25, low = 50)
            throw AssertionError("expected a failure for an inverted pair")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("25"))
        }
    }

    @Test
    fun `the face is a bare number so the digits can be drawn larger`() {
        assertEquals("100", VolumeToggle.face(16, 16, VolumeToggle.LOUD))
        assertEquals("25", VolumeToggle.face(4, 16, VolumeToggle.QUIET))
        assertEquals("--", VolumeToggle.face(0, 0, VolumeToggle.LOUD))
    }
}

class TileTextTest {

    @Test
    fun `every channel has a readable four-letter name`() {
        val names = Volume.STREAMS.map { TileText.four(it.label) }
        assertEquals(listOf("CALL", "MEDI", "RING", "NOTF", "ALRM"), names)
        names.forEach { assertEquals(4, it.length) }
    }

    @Test
    fun `the names are chosen, not truncated`() {
        // "ALARM".take(4) is "ALAR", which reads as nothing.
        assertEquals("ALRM", TileText.four("Alarm"))
        assertEquals("NOTF", TileText.four("Notification"))
    }

    @Test
    fun `an unknown channel still yields four characters rather than crashing`() {
        assertEquals("BLUE", TileText.four("Bluetooth"))
        assertEquals("----", TileText.four("123"))
    }

    @Test
    fun `the label names the level and which pair it toggles`() {
        assertEquals("Media 100%  (50/100)", TileText.label("Media", 16, 16, VolumeToggle.LOUD))
        assertEquals("Alarm 50%  (25/50)", TileText.label("Alarm", 8, 16, VolumeToggle.QUIET))
        assertEquals("Call unavailable", TileText.label("Call", 3, 0, VolumeToggle.LOUD))
    }

    @Test
    fun `the subtitle says what the next press does, not what the state is`() {
        assertEquals("tap for 50%", TileText.nextAction(15, 15, VolumeToggle.LOUD))
        assertEquals("tap for 100%", TileText.nextAction(4, 15, VolumeToggle.LOUD))
        assertEquals("no range on this device", TileText.nextAction(0, 0, VolumeToggle.LOUD))
    }
}

class NeedsTest {

    @Test
    fun `call media and alarm need nothing at all, ever`() {
        listOf(0, 3, 4).forEach { stream ->
            assertEquals(Need.NOTHING, Needs.forVolume(stream, dndActive = false, policyGranted = false))
            assertEquals(Need.NOTHING, Needs.forVolume(stream, dndActive = true, policyGranted = false))
        }
    }

    @Test
    fun `ring and notification need nothing until Do Not Disturb is on`() {
        listOf(2, 5).forEach { stream ->
            assertEquals(Need.NOTHING, Needs.forVolume(stream, dndActive = false, policyGranted = false))
            assertEquals(Need.SETTINGS_TOGGLE, Needs.forVolume(stream, dndActive = true, policyGranted = false))
            assertEquals(Need.NOTHING, Needs.forVolume(stream, dndActive = true, policyGranted = true))
        }
    }

    @Test
    fun `there is no longer any state that could require a privileged shell`() {
        // v18: the enum has two entries and neither is SHIZUKU. This is the guard against it
        // creeping back in as a third.
        assertEquals(2, Need.values().size)
        assertEquals("works out of the box", Needs.describe(Need.NOTHING))
    }
}

class FeedbackTest {

    @Test
    fun `an empty result still says something`() {
        assertEquals("Done", Feedback.resultLabel(""))
        assertEquals("Done", Feedback.resultLabel("   "))
    }

    @Test
    fun `a real message is passed through trimmed`() {
        assertEquals("Centred", Feedback.resultLabel("  Centred  "))
    }

    @Test
    fun `the hold is long enough to read and short enough not to feel stuck`() {
        assertTrue(Feedback.HOLD_MS in 1200..4000)
    }
}

/**
 * The 27 bug, and the range.
 */
class DisplayTest {

    @Test
    fun `the call stream has fifteen steps and cannot land on 25 exactly`() {
        // The arithmetic behind the report: 25% of 15 is 3.75, step 4 of 15 is 26.7%, shown 27.
        assertEquals(4, Volume.indexFor(25, 15))
        assertEquals(27, Volume.percentFor(4, 15))
    }

    @Test
    fun `a tile asked for 25 shows 25, on a fifteen step stream`() {
        assertEquals(25, Volume.displayPercent(4, 15, VolumeToggle.QUIET))
        assertEquals(50, Volume.displayPercent(8, 15, VolumeToggle.QUIET))
        assertEquals(50, Volume.displayPercent(8, 15, VolumeToggle.LOUD))
        assertEquals(100, Volume.displayPercent(15, 15, VolumeToggle.LOUD))
    }

    @Test
    fun `a sixteen step stream was never wrong and is not changed`() {
        assertEquals(25, Volume.displayPercent(4, 16, VolumeToggle.QUIET))
        assertEquals(50, Volume.displayPercent(8, 16, VolumeToggle.LOUD))
        assertEquals(100, Volume.displayPercent(16, 16, VolumeToggle.LOUD))
    }

    @Test
    fun `a level set from somewhere else is reported honestly, not snapped`() {
        // The line between rounding and lying. 60% is not the tile's doing and must not be
        // dressed up as 50, or the face would claim a state the tile did not put there.
        assertEquals(63, Volume.displayPercent(10, 16, VolumeToggle.LOUD))
        assertEquals(75, Volume.displayPercent(12, 16, VolumeToggle.LOUD))
        assertEquals(0, Volume.displayPercent(0, 16, VolumeToggle.QUIET))
    }

    @Test
    fun `snapping never reaches further than half a step`() {
        // On a 16 step stream half a step is about 3 points, so 31% must stay 31 and not
        // become 25 or 50.
        assertEquals(31, Volume.displayPercent(5, 16, VolumeToggle.QUIET))
    }

    @Test
    fun `a stream with no range shows nothing rather than zero percent`() {
        assertEquals(0, Volume.displayPercent(0, 0, VolumeToggle.LOUD))
        assertEquals("--", VolumeToggle.face(0, 0, VolumeToggle.LOUD))
    }

    @Test
    fun `the range names both ends, low first, so the two tiles differ on sight`() {
        assertEquals("50/100", TileText.range(VolumeToggle.LOUD))
        assertEquals("25/50", TileText.range(VolumeToggle.QUIET))
    }

    @Test
    fun `the two tiles for one channel can be told apart when both read 50`() {
        // Exactly the confusion reported: both faces show 50 and only the range distinguishes
        // whether the next press goes up or down.
        val loud = TileText.badge(8, 16, VolumeToggle.LOUD)
        val quiet = TileText.badge(8, 16, VolumeToggle.QUIET)
        assertEquals(loud, quiet)
        assertTrue(TileText.range(VolumeToggle.LOUD) != TileText.range(VolumeToggle.QUIET))
    }
}

/**
 * The meter's numbers, ported from TTT Mini, and the normalise gain.
 */
class MeterTest {

    @Test
    fun `silence sits on the floor, not below it`() {
        assertEquals(Meter.FLOOR_DB, Meter.toDb(0f), 0.01f)
        assertEquals(Meter.FLOOR_DB, Meter.toDb(0.0001f), 0.01f)
        assertEquals(0f, Meter.norm(Meter.FLOOR_DB), 0.01f)
    }

    @Test
    fun `full scale is zero dB and the top of the bar`() {
        assertEquals(0f, Meter.toDb(1f), 0.01f)
        assertEquals(1f, Meter.norm(0f), 0.01f)
    }

    @Test
    fun `half amplitude is about six dB down, which is the arithmetic that makes it a meter`() {
        assertEquals(-6.02f, Meter.toDb(0.5f), 0.05f)
    }

    @Test
    fun `a negative sample reads the same as its positive twin`() {
        // Waveform data swings both ways; a meter that only saw one half would read 6 dB low.
        assertEquals(Meter.toDb(0.5f), Meter.toDb(-0.5f), 0.001f)
    }

    @Test
    fun `attack is instant and release is gradual`() {
        assertEquals(-10f, Meter.smooth(-40f, -10f), 0.01f)      // rises at once
        val falling = Meter.smooth(-10f, -40f)
        assertTrue("must fall", falling < -10f)
        assertTrue("must not snap", falling > -40f)
    }

    @Test
    fun `the peak mark holds then decays, and never below the floor`() {
        assertEquals(-5f, Meter.decayPeak(-20f, -5f), 0.01f)
        assertEquals(-5.6f, Meter.decayPeak(-5f, -50f), 0.01f)
        assertEquals(Meter.FLOOR_DB, Meter.decayPeak(Meter.FLOOR_DB, Meter.FLOOR_DB), 0.01f)
    }

    @Test
    fun `the three colours change at the documented thresholds, both sides`() {
        assertEquals(0xFF56D364.toInt(), Meter.colourFor(-12.1f))
        assertEquals(0xFFF0883E.toInt(), Meter.colourFor(-11.9f))
        assertEquals(0xFFF0883E.toInt(), Meter.colourFor(-3.1f))
        assertEquals(0xFF9B3B33.toInt(), Meter.colourFor(-2.9f))
    }
}

class NormalizeTest {

    @Test
    fun `silence yields no gain, because a gain computed from silence is noise`() {
        assertEquals(0, Normalize.gainMb(Meter.FLOOR_DB))
        assertEquals(0, Normalize.gainMb(-60f))
        assertEquals(false, Normalize.hasSignal(-60f))
    }

    @Test
    fun `a quiet signal is lifted to just under full scale`() {
        // -20 dB peak needs 19 dB to reach the -1 dB target. 1900 millibels.
        assertEquals(1900, Normalize.gainMb(-20f))
        assertEquals(900, Normalize.gainMb(-10f))
    }

    @Test
    fun `something already loud is left alone rather than pushed into clipping`() {
        assertEquals(0, Normalize.gainMb(-1f))
        assertEquals(0, Normalize.gainMb(-0.5f))
        assertEquals(0, Normalize.gainMb(0f))
    }

    @Test
    fun `gain is capped, so a near-silent passage cannot ask for forty decibels`() {
        // -49 dB is just above the silence gate, and would want 48 dB without the cap.
        assertTrue(Normalize.hasSignal(-49f))
        assertEquals(Normalize.MAX_GAIN_MB, Normalize.gainMb(-49f))
    }

    @Test
    fun `the silence gate is exact on both sides`() {
        assertEquals(false, Normalize.hasSignal(Normalize.SILENCE_DB))
        assertTrue(Normalize.hasSignal(Normalize.SILENCE_DB + 0.1f))
    }

    @Test
    fun `the description speaks decibels, because millibels mean nothing to a reader`() {
        assertEquals("already at full level", Normalize.describe(0))
        assertEquals("boosting 19.0 dB", Normalize.describe(1900))
        assertEquals("boosting 6.5 dB", Normalize.describe(650))
    }
}
