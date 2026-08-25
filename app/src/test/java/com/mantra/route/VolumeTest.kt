package com.mantra.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test



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

/**
 * The stuck-cycle defect, found by driving the cycle through the real index round-trip.
 */
class CycleTest {

    private fun visits(max: Int, presses: Int = 12): Set<Int> {
        var index = Volume.indexFor(100, max)
        val seen = mutableSetOf<Int>()
        repeat(presses) {
            index = Presets.nextIndex(index, max)
            seen.add(Presets.snap(Volume.percentFor(index, max), max))
        }
        return seen
    }

    @Test
    fun `no stream can get stuck on one level, however coarse its range`() {
        // A two-step stream used to visit exactly one level for ever, with every press looking
        // like it worked. That is the failure this whole test exists for.
        listOf(1, 2, 3, 5, 7, 10, 15, 16, 20, 30, 100).forEach { max ->
            assertTrue("max=$max visited ${visits(max)}", visits(max).size >= 2)
        }
    }

    @Test
    fun `an ordinary stream still visits all four presets`() {
        listOf(15, 16, 20, 30, 100).forEach { max ->
            assertEquals("max=$max", setOf(25, 50, 75, 100), visits(max))
        }
    }

    @Test
    fun `the cycle closes, so the same presses repeat the same levels`() {
        listOf(15, 16).forEach { max ->
            var index = Volume.indexFor(100, max)
            val first = (1..4).map { Presets.nextIndex(index, max).also { i -> index = i } }
            val second = (1..4).map { Presets.nextIndex(index, max).also { i -> index = i } }
            assertEquals("max=$max", first, second)
        }
    }

    @Test
    fun `snapping never reaches more than half the gap between presets`() {
        // Uncapped, the tolerance on a coarse stream grew past 25 points and every level
        // snapped to the same preset — half of how the cycle got stuck. Capped at 12, which is
        // half the 25 point gap, a value further than 12 from every preset is left alone.
        assertEquals(12, Presets.snap(12, 1))    // 13 from 25, the nearest preset
        assertEquals(50, Presets.snap(38, 2))    // exactly 12 from 50, so it snaps
        // and on a normal stream the tolerance is the real half step, so 38 is left alone
        assertEquals(38, Presets.snap(38, 16))
    }

    @Test
    fun `a stream with no range yields step zero rather than dividing by anything`() {
        assertEquals(0, Presets.nextIndex(0, 0))
        assertEquals(0, Presets.nextIndex(5, -1))
    }
}

/**
 * The volume arithmetic. RESTORED: a regex removing the meter suites in v23 took this and
 * FeedbackTest with them, and the count dropping from 41 to 18 is the only reason it was
 * noticed. Counting the tests is not ceremony.
 */
class VolumeMathTest {

    @Test
    fun `the top of the range is reachable`() {
        // Truncation puts 99% at 4 of 5 and the slider never reaches maximum.
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

/** A control that goes blank on press is the bug this exists to prevent. RESTORED with the above. */
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
