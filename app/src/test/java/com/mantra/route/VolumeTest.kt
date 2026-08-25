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
    fun `full is exact at the boundary`() {
        assertEquals(false, Presets.isFull(99))
        assertTrue(Presets.isFull(100))
    }
}

class FaceTest {

    @Test
    fun `one letter per channel, and all five differ`() {
        // The whole basis of the single letter: C M R N A collide with nothing. If a sixth
        // channel ever starts with one of these, this fails loudly instead of quietly drawing
        // two identical tiles.
        val one = Volume.STREAMS.map { TileText.one(it.label) }
        assertEquals(listOf("C", "M", "R", "N", "A"), one)
        assertEquals(5, one.toSet().size)
        one.forEach { assertEquals(1, it.length) }
    }

    @Test
    fun `the face is two letters and the level, so it grows with the number`() {
        // v26: the level is a real percentage now, so a face is 4 or 5 characters. The uniform
        // text size is computed against the widest, which is why this length matters.
        assertEquals("C100", TileText.face("Call", 100, 15))
        assertEquals("M50", TileText.face("Media", 50, 16))
        assertEquals("C25", TileText.face("Call", 27, 15))
        listOf(25, 50, 75).forEach {
            assertEquals(3, TileText.face("Media", it, 16).length)
        }
        assertEquals(4, TileText.face("Media", 100, 16).length)
    }

    @Test
    fun `a stream with no range says so rather than showing a level`() {
        assertEquals("C--", TileText.face("Call", 0, 0))
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
    fun `an unknown channel still yields a letter rather than crashing`() {
        assertEquals("B", TileText.one("Bluetooth"))
        assertEquals("-", TileText.one("123"))
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

/**
 * Elevator logic. Up to the top, back down, up again — never a jump from loudest to quietest.
 */
class ElevatorTest {

    /** Walk the tile the way a thumb does, returning the sequence of levels reached. */
    private fun walk(max: Int, presses: Int, startAt: Int = 25): List<Int> {
        var index = Volume.indexFor(startAt, max)
        var up = true
        return (1..presses).map {
            val step = Elevator.step(index, max, up)
            index = step.index; up = step.goingUp
            Presets.snap(Volume.percentFor(index, max), max)
        }
    }

    @Test
    fun `it never jumps from the top straight to the bottom`() {
        // The whole complaint: 100 followed by 25.
        val seq = walk(16, 10)
        seq.zipWithNext().forEach { (a, b) ->
            assertTrue("jumped $a -> $b in $seq", !(a == 100 && b == 25))
            assertTrue("jumped $a -> $b in $seq", !(a == 25 && b == 100))
        }
    }

    @Test
    fun `it goes up to the top then turns round`() {
        assertEquals(listOf(50, 75, 100, 75, 50, 25, 50, 75), walk(16, 8))
    }

    @Test
    fun `it turns round at the bottom too`() {
        assertEquals(listOf(75, 50, 25, 50, 75, 100), walk(16, 6, startAt = 100).let {
            // starting at 100 going "up" must reverse immediately
            it
        })
    }

    @Test
    fun `pressing at an end moves, it does not stall`() {
        // Reverse AND move in the same press. Reversing without moving would look like a dead
        // tile, which is the failure this whole session keeps returning to.
        val atTop = Elevator.step(Volume.indexFor(100, 16), 16, goingUp = true)
        assertEquals(Volume.indexFor(75, 16), atTop.index)
        assertEquals(false, atTop.goingUp)

        val atBottom = Elevator.step(Volume.indexFor(25, 16), 16, goingUp = false)
        assertEquals(Volume.indexFor(50, 16), atBottom.index)
        assertTrue(atBottom.goingUp)
    }

    @Test
    fun `a level set from the system panel is matched to the nearest stop, not ignored`() {
        // 60% sits between 50 and 75. Requiring an exact match would stall the tile.
        val step = Elevator.step(Volume.indexFor(60, 16), 16, goingUp = true)
        assertEquals(Volume.indexFor(75, 16), step.index)
    }

    @Test
    fun `the fifteen step call stream behaves the same`() {
        assertEquals(listOf(50, 75, 100, 75, 50, 25), walk(15, 6))
    }

    @Test
    fun `a stream with no range yields step zero rather than throwing`() {
        assertEquals(0, Elevator.step(0, 0, true).index)
        assertEquals(0, Elevator.step(3, -1, false).index)
    }

    @Test
    fun `every stop is visited over a long walk`() {
        assertEquals(setOf(25, 50, 75, 100), walk(16, 20).toSet())
    }
}

/**
 * The face carries the real percentage, and a press still lands on a preset.
 * Two requirements that pull in opposite directions.
 */
class PercentFaceTest {

    @Test
    fun `the face shows the level that is actually set, not the nearest preset`() {
        assertEquals("M63", TileText.face("Media", 63, 16))
        assertEquals("M31", TileText.face("Media", 31, 16))
        assertEquals("C100", TileText.face("Call", 100, 15))
    }

    @Test
    fun `a level the hardware cannot land on exactly still reads as what it was asked for`() {
        // 25% of the fifteen-step Call stream is 26.7%. It was asked for 25 and 25 is the
        // closest it can get, so 25 is what it says.
        assertEquals("C25", TileText.face("Call", 27, 15))
    }

    @Test
    fun `the whole slider produces many different faces, not four`() {
        // The point of following the fine setting: if this collapsed to four the app's sliders
        // and the tiles would disagree everywhere in between.
        val faces = (0..16).map { TileText.face("Media", Volume.percentFor(it, 16), 16) }.toSet()
        assertTrue("only ${faces.size} distinct faces", faces.size > 8)
    }

    @Test
    fun `ten and one hundred are no longer the same face`() {
        // The single digit could not tell them apart and leaned on colour to do it.
        assertTrue(TileText.face("Media", 10, 16) != TileText.face("Media", 100, 16))
    }

    @Test
    fun `a stream with no range shows dashes, not a zero`() {
        assertEquals("C--", TileText.face("Call", 0, 0))
    }
}

class ElevatorDirectionTest {

    @Test
    fun `from silence the first press goes to the lowest preset, not past it`() {
        // "Nearest stop then step" sent 0% to 50, skipping 25 entirely.
        val step = Elevator.step(0, 16, goingUp = true)
        assertEquals(Volume.indexFor(25, 16), step.index)
        assertTrue(step.goingUp)
    }

    @Test
    fun `just below the top, pressing up reaches the top rather than turning round`() {
        // 94% used to go DOWN to 75, because it snapped to 100 first and then stepped back.
        val step = Elevator.step(15, 16, goingUp = true)
        assertEquals(Volume.indexFor(100, 16), step.index)
        assertTrue(step.goingUp)
    }

    @Test
    fun `at the very top it turns round and moves`() {
        val step = Elevator.step(Volume.indexFor(100, 16), 16, goingUp = true)
        assertEquals(Volume.indexFor(75, 16), step.index)
        assertEquals(false, step.goingUp)
    }

    @Test
    fun `at the very bottom it turns round and moves`() {
        val step = Elevator.step(Volume.indexFor(25, 16), 16, goingUp = false)
        assertEquals(Volume.indexFor(50, 16), step.index)
        assertTrue(step.goingUp)
    }

    @Test
    fun `every press lands exactly on a preset, from any starting level`() {
        val stops = Presets.stops(16)
        for (index in 0..16) {
            for (up in listOf(true, false)) {
                assertTrue("from $index up=$up", Elevator.step(index, 16, up).index in stops)
            }
        }
    }
}

/** The line across the top of the screen. */
class BannerTest {

    @Test
    fun `it names the channel and the level, in capitals`() {
        assertEquals("MEDIA  25%", TileText.banner("Media", 25, 16))
        assertEquals("CALL  100%", TileText.banner("Call", 100, 15))
    }

    @Test
    fun `it reports the level that was reached, snapping only where the hardware cannot land`() {
        assertEquals("CALL  25%", TileText.banner("Call", 27, 15))
        assertEquals("MEDIA  63%", TileText.banner("Media", 63, 16))
    }

    @Test
    fun `a stream with no range says so rather than claiming zero percent`() {
        assertEquals("CALL  NO RANGE", TileText.banner("Call", 0, 0))
    }
}
