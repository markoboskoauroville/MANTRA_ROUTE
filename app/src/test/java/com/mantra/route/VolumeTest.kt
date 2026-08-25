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

/**
 * The four-preset cycle, the one-character digit, and the two-letter face.
 */
class PresetsTest {

    @Test
    fun `the cycle steps up and wraps at the top`() {
        assertEquals(50, Presets.next(25))
        assertEquals(75, Presets.next(50))
        assertEquals(100, Presets.next(75))
        assertEquals(25, Presets.next(100))
    }

    @Test
    fun `a level set from somewhere else lands on the next preset above it`() {
        // 63 goes to 75, not back to the start. The alternative — snap to nearest then step —
        // sends a level just under 75 all the way to 100.
        assertEquals(75, Presets.next(63))
        assertEquals(50, Presets.next(26))
        assertEquals(25, Presets.next(0))
    }

    @Test
    fun `the fifteen step call stream still reads as the preset it was aimed at`() {
        // 25% of 15 steps lands on 26.7%. Half a step is about 3, so it snaps back to 25.
        assertEquals(25, Presets.snap(27, 15))
        assertEquals(50, Presets.snap(53, 15))
        assertEquals(75, Presets.snap(73, 15))
        assertEquals(100, Presets.snap(100, 15))
    }

    @Test
    fun `a level further than half a step from a preset is reported honestly`() {
        assertEquals(63, Presets.snap(63, 16))
        assertEquals(38, Presets.snap(38, 16))
    }

    @Test
    fun `the digit is the leading one, and 100 is a bare 1`() {
        assertEquals("2", Presets.digit(25))
        assertEquals("5", Presets.digit(50))
        assertEquals("7", Presets.digit(75))
        assertEquals("1", Presets.digit(100))
    }

    @Test
    fun `the 1 collision is real and is resolved by the white state, not by the digit`() {
        // A tenth also leads with 1. Nothing in the text can separate them, so the test pins
        // that the STATE does: only 100 is full.
        assertEquals("1", Presets.digit(10))
        assertEquals("1", Presets.digit(100))
        assertTrue(Presets.isFull(100))
        assertEquals(false, Presets.isFull(10))
    }

    @Test
    fun `below ten there is no leading digit to show`() {
        assertEquals("0", Presets.digit(5))
        assertEquals("0", Presets.digit(0))
        assertEquals(false, Presets.isFull(0))
    }

    @Test
    fun `full is exact at the boundary`() {
        assertEquals(false, Presets.isFull(99))
        assertTrue(Presets.isFull(100))
    }
}

class FaceTest {

    @Test
    fun `two letters per channel, and all five differ`() {
        val two = Volume.STREAMS.map { TileText.two(it.label) }
        assertEquals(listOf("CA", "ME", "RI", "NO", "AL"), two)
        assertEquals(5, two.toSet().size)
        two.forEach { assertEquals(2, it.length) }
    }

    @Test
    fun `the face is three characters, always`() {
        assertEquals("CA1", TileText.face("Call", 100, 15))
        assertEquals("ME5", TileText.face("Media", 50, 16))
        assertEquals("CA2", TileText.face("Call", 27, 15))
        listOf(25, 50, 75, 100).forEach {
            assertEquals(3, TileText.face("Media", it, 16).length)
        }
    }

    @Test
    fun `a stream with no range says so rather than showing a level`() {
        assertEquals("CA-", TileText.face("Call", 0, 0))
        assertEquals("Call unavailable", TileText.label("Call", 0, 0))
        assertEquals("Call has no volume range on this phone", TileText.spoken("Call", 0, 0))
    }

    @Test
    fun `the bubble says a whole sentence, not a bare number`() {
        // "100%" alone would not tell you which tile you pressed, which is the case it is for.
        assertEquals("Call 100%", TileText.spoken("Call", 100, 15))
        assertEquals("Media 25%", TileText.spoken("Media", 25, 16))
        assertTrue(TileText.spoken("Alarm", 50, 16).startsWith("Alarm"))
    }

    @Test
    fun `an unknown channel still yields two characters rather than crashing`() {
        assertEquals("BL", TileText.two("Bluetooth"))
        assertEquals("--", TileText.two("123"))
    }
}
