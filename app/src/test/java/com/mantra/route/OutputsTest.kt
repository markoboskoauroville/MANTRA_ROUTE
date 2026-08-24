package com.mantra.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST 1 — the mechanism, alone. No Android, no device, no notification.
 *
 * The question each case answers is the one from modules/four-tests.md: what could be true
 * that would make this pass while the feature is still broken? Cases here are chosen against
 * the rule the code claims to follow, not to walk the happy path from four angles.
 */
class OutputsTest {

    private fun out(id: Int, type: Int, name: String = "", address: String = "") =
        Output(id, type, name, address)

    // ---- the case it is FOR ---------------------------------------------------------------

    @Test
    fun `speaker and earpiece both survive and are named apart`() {
        val rows = Outputs.rows(
            listOf(
                out(1, AudioType.EARPIECE, "Nothing Phone (2a)"),
                out(2, AudioType.SPEAKER, "Nothing Phone (2a)"),
            )
        )
        assertEquals(listOf("Speaker", "Earpiece"), rows.map { it.label })
    }

    // ---- the case it must REFUSE ----------------------------------------------------------

    @Test
    fun `telephony and remote submix are never offered as destinations`() {
        val rows = Outputs.rows(
            listOf(
                out(1, AudioType.SPEAKER),
                out(2, AudioType.TELEPHONY),
                out(3, AudioType.REMOTE_SUBMIX),
                out(4, AudioType.BUS),
                out(5, AudioType.SPEAKER_SAFE),
            )
        )
        assertEquals(1, rows.size)
        assertEquals("Speaker", rows.first().label)
    }

    @Test
    fun `one headset reported under three profiles becomes one row, and not the SCO one`() {
        val rows = Outputs.rows(
            listOf(
                out(10, AudioType.BLUETOOTH_SCO, "Nothing Ear", "AA:BB:CC:DD:EE:FF"),
                out(11, AudioType.BLUETOOTH_A2DP, "Nothing Ear", "AA:BB:CC:DD:EE:FF"),
                out(12, AudioType.BLE_HEADSET, "Nothing Ear", "AA:BB:CC:DD:EE:FF"),
            )
        )
        assertEquals(1, rows.size)
        assertEquals("Nothing Ear", rows.first().label)
        assertEquals(Glyph.BLE, rows.first().glyph)
    }

    @Test
    fun `two different headsets sharing a product name stay two rows`() {
        // This is the case a name-based dedup gets wrong, and it is not hypothetical: two
        // identical pairs of the same earbuds report the same productName.
        val rows = Outputs.rows(
            listOf(
                out(10, AudioType.BLUETOOTH_A2DP, "Nothing Ear", "AA:BB:CC:DD:EE:01"),
                out(11, AudioType.BLUETOOTH_A2DP, "Nothing Ear", "AA:BB:CC:DD:EE:02"),
            )
        )
        assertEquals(2, rows.size)
    }

    @Test
    fun `without addresses two same-named headsets collapse, which is the documented cost`() {
        // When BLUETOOTH_CONNECT is not granted the address is empty. Collapsing is the
        // deliberate behaviour: one row that works beats two rows that cannot be told apart.
        val rows = Outputs.rows(
            listOf(
                out(10, AudioType.BLUETOOTH_A2DP, "Nothing Ear", ""),
                out(11, AudioType.BLUETOOTH_A2DP, "Nothing Ear", ""),
            )
        )
        assertEquals(1, rows.size)
    }

    // ---- both sides of every boundary -----------------------------------------------------

    @Test
    fun `empty in, empty out`() {
        assertEquals(emptyList<Row>(), Outputs.rows(emptyList()))
    }

    @Test
    fun `a list of nothing but hidden types produces no rows rather than a crash`() {
        assertEquals(emptyList<Row>(), Outputs.rows(listOf(out(1, AudioType.TELEPHONY))))
    }

    @Test
    fun `an unknown future type code is still shown rather than silently dropped`() {
        // A type this build has never heard of is more likely to be a new kind of headphone
        // than something to hide. Dropping it would be invisible.
        val rows = Outputs.rows(listOf(out(1, 99, "Something New")))
        assertEquals(1, rows.size)
        assertEquals("Something New", rows.first().label)
    }

    // ---- the same input twice --------------------------------------------------------------

    @Test
    fun `order is stable and does not depend on discovery order`() {
        val a = listOf(
            out(1, AudioType.BLUETOOTH_A2DP, "Ear", "01"),
            out(2, AudioType.SPEAKER),
            out(3, AudioType.WIRED_HEADPHONES),
        )
        val b = a.reversed()
        assertEquals(Outputs.rows(a), Outputs.rows(b))
        assertEquals(
            listOf("Speaker", "Wired headphones", "Ear"),
            Outputs.rows(a).map { it.label },
        )
    }

    @Test
    fun `running it twice on the same input gives the same thing`() {
        val input = listOf(out(1, AudioType.SPEAKER), out(2, AudioType.USB_HEADSET, "DAC"))
        assertEquals(Outputs.rows(input), Outputs.rows(input))
    }

    // ---- naming ----------------------------------------------------------------------------

    @Test
    fun `a built-in output ignores the model name the platform hands back`() {
        assertEquals("Speaker", Outputs.labelFor(AudioType.SPEAKER, "Nothing Phone (2a)"))
        assertEquals("Earpiece", Outputs.labelFor(AudioType.EARPIECE, "Nothing Phone (2a)"))
    }

    @Test
    fun `a removable output with no name gets a kind rather than an empty row`() {
        assertEquals("USB headset", Outputs.labelFor(AudioType.USB_HEADSET, ""))
        assertEquals("USB headset", Outputs.labelFor(AudioType.USB_HEADSET, "   "))
    }
}

/**
 * TEST 1 for the two rules v2 added: the key that survives a reconnection, and storing what is
 * switched OFF rather than what is on.
 */
class ArrangementTest {

    private fun out(id: Int, type: Int, name: String = "", address: String = "") =
        Output(id, type, name, address)

    @Test
    fun `the key survives the id changing, which is what happens on every reconnection`() {
        val first = Outputs.rows(listOf(out(42, AudioType.WIRED_HEADPHONES))).single()
        val again = Outputs.rows(listOf(out(77, AudioType.WIRED_HEADPHONES))).single()
        assertTrue(first.id != again.id)
        assertEquals(first.key, again.key)
    }

    @Test
    fun `two different outputs never share a key`() {
        val rows = Outputs.rows(
            listOf(
                out(1, AudioType.SPEAKER),
                out(2, AudioType.EARPIECE),
                out(3, AudioType.WIRED_HEADPHONES),
                out(4, AudioType.BLUETOOTH_A2DP, "Ear", "01"),
                out(5, AudioType.BLUETOOTH_A2DP, "Speaker", "02"),
            )
        )
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `the key does not care about the case the platform reports a name in`() {
        assertEquals(
            Outputs.keyOf(AudioType.BLUETOOTH_A2DP, "Nothing Ear"),
            Outputs.keyOf(AudioType.BLUETOOTH_A2DP, "nothing ear"),
        )
    }

    @Test
    fun `switching one off removes exactly that row`() {
        val rows = Outputs.rows(
            listOf(out(1, AudioType.SPEAKER), out(2, AudioType.EARPIECE))
        )
        val earpiece = rows.first { it.label == "Earpiece" }
        val visible = Outputs.visible(rows, setOf(earpiece.key))
        assertEquals(listOf("Speaker"), visible.map { it.label })
    }

    /**
     * The whole reason §7 says store the exclusions. If the set held what to SHOW, an output
     * kind that did not exist when the set was saved would be missing and there would be no
     * way to discover that it should be there.
     */
    @Test
    fun `an output never seen before is shown, because the set holds exclusions`() {
        val rows = Outputs.rows(listOf(out(1, AudioType.SPEAKER), out(2, 99, "Future Thing")))
        val off = setOf(Outputs.keyOf(AudioType.SPEAKER, "Speaker"))
        assertEquals(listOf("Future Thing"), Outputs.visible(rows, off).map { it.label })
    }

    @Test
    fun `an empty off-set changes nothing, and a stale key in it changes nothing either`() {
        val rows = Outputs.rows(listOf(out(1, AudioType.SPEAKER)))
        assertEquals(rows, Outputs.visible(rows, emptySet()))
        assertEquals(rows, Outputs.visible(rows, setOf("999:something that is gone")))
    }

    @Test
    fun `switching everything off gives an empty list rather than falling back to all`() {
        val rows = Outputs.rows(listOf(out(1, AudioType.SPEAKER), out(2, AudioType.EARPIECE)))
        assertEquals(emptyList<Row>(), Outputs.visible(rows, rows.map { it.key }.toSet()))
    }
}

class BlendMathTest {

    /**
     * The one that matters. If this ever starts returning a number, a control that cannot work
     * has quietly become one that claims to.
     */
    

    

    @Test
    fun `balance is clamped at both ends and at the ends exactly`() {
        assertEquals(-1.0f, BlendMath.clampBalance(-4f), 0.0001f)
        assertEquals(1.0f, BlendMath.clampBalance(4f), 0.0001f)
        assertEquals(-1.0f, BlendMath.clampBalance(-1.0f), 0.0001f)
        assertEquals(1.0f, BlendMath.clampBalance(1.0f), 0.0001f)
        assertEquals(0.0f, BlendMath.clampBalance(0f), 0.0001f)
    }

    
}

/**
 * The regression suite for the v2 failure.
 *
 * Every one of these would have passed in v2 too — the bug was never in the parsing, it was in
 * asking the remote process a question binder could not answer. What these lock down is the
 * replacement: that the exit code now travels in the output, and that reading it back is not
 * fooled by output which happens to contain the token.
 */
class ExitMarkerTest {

    @Test
    fun `wrap appends a line that prints the status`() {
        assertEquals("id -u\necho __MR_EXIT__\$?", ExitMarker.wrap("id -u"))
    }

    @Test
    fun `a clean run yields its output and zero`() {
        val p = ExitMarker.parse("2000\n__MR_EXIT__0")
        assertEquals(0, p.code)
        assertEquals("2000", p.output)
        assertTrue(p.found)
    }

    @Test
    fun `a failed command keeps its non-zero code`() {
        val p = ExitMarker.parse("cmd: not found\n__MR_EXIT__127")
        assertEquals(127, p.code)
        assertEquals("cmd: not found", p.output)
    }

    /**
     * The case that matters. `settings list secure` and the swap probe both grep for text that
     * can contain the token; taking the FIRST match would read a command's own output as its
     * exit status.
     */
    @Test
    fun `the marker is read from the end when output contains the token`() {
        val p = ExitMarker.parse("__MR_EXIT__99 appears in a line of output\nreal output\n__MR_EXIT__0")
        assertEquals(0, p.code)
        assertTrue(p.output.contains("real output"))
        assertTrue(p.output.contains("99 appears"))
    }

    @Test
    fun `no marker is not the same as exit zero`() {
        // The v2 shape of failure: the shell died before printing anything. Reporting this as
        // success is the exact mistake that let nine probes claim a verdict they never had.
        val p = ExitMarker.parse("half a line of output")
        assertEquals(false, p.found)
        assertEquals(-1, p.code)
        assertEquals("half a line of output", p.output)
    }

    @Test
    fun `empty output with a marker is still a real result`() {
        val p = ExitMarker.parse("__MR_EXIT__0")
        assertEquals(0, p.code)
        assertEquals("", p.output)
        assertTrue(p.found)
    }

    @Test
    fun `a marker with garbage after it is a fault, not a code`() {
        val p = ExitMarker.parse("out\n__MR_EXIT__notanumber")
        assertEquals(-1, p.code)
        assertTrue(p.found)
    }

    @Test
    fun `trailing newline from the shell does not break the read`() {
        val p = ExitMarker.parse("2000\n__MR_EXIT__0\n")
        assertEquals(0, p.code)
        assertEquals("2000", p.output)
    }
}

/**
 * The regression suite for the v3 phone run.
 *
 * The probe restored a secure setting and claimed success without looking. On the device the
 * restore did not land, the test value stuck, and the following run read it back as the user's
 * own setting — leaving the phone in mono. These lock down the read-back.
 */
class ShellTextTest {

    @Test
    fun `an unset key is restored by deleting it, not by writing the word null`() {
        // Writing the literal string "null" is how a key that was never set becomes a key set
        // to nonsense.
        assertEquals("settings delete secure master_mono", ShellText.restoreCommand("master_mono", "null"))
        assertEquals("settings delete secure master_mono", ShellText.restoreCommand("master_mono", ""))
        assertEquals("settings delete secure master_mono", ShellText.restoreCommand("master_mono", "   "))
    }

    @Test
    fun `a set key is restored by writing its old value back`() {
        assertEquals("settings put secure master_mono 1", ShellText.restoreCommand("master_mono", "1"))
        assertEquals("settings put secure master_balance -0.4", ShellText.restoreCommand("master_balance", " -0.4 "))
    }

    @Test
    fun `null and empty are the same state, so that restore is not reported as failed`() {
        assertTrue(ShellText.restored("null", ""))
        assertTrue(ShellText.restored("", "null"))
        assertTrue(ShellText.restored("null", "null"))
    }

    @Test
    fun `the exact device failure is caught`() {
        // master_mono was unset, the probe wrote 1, the delete did not land, it read back 1.
        assertEquals(false, ShellText.restored("null", "1"))
        assertEquals(false, ShellText.restored("null", "0.5"))
    }

    @Test
    fun `a genuine restore passes`() {
        assertTrue(ShellText.restored("1", "1"))
        assertTrue(ShellText.restored("0.5", " 0.5 "))
        assertEquals(false, ShellText.restored("0", "1"))
    }

    @Test
    fun `a service with no shell commands is not a capability`() {
        // Verbatim from the phone: cmd media_router scored WORKS on this.
        assertEquals(false, ShellText.cmdUsable("No shell command implementation."))
        assertEquals(false, ShellText.cmdUsable(""))
        assertEquals(false, ShellText.cmdUsable("   "))
        assertEquals(false, ShellText.cmdUsable("Unknown command: help"))
    }

    @Test
    fun `a service with real help text is a capability`() {
        assertTrue(ShellText.cmdUsable("Bluetooth Manager Commands:\n  help or -h\n  enable"))
    }
}

/**
 * The regression suite for "only colors are changing".
 *
 * Reported from the phone, 23.8.2026. The panel lit the row that was tapped, from the call
 * route or, failing that, from the app's own memory of the tap. Neither is evidence about
 * where music is playing.
 */
class ClaimTest {

    @Test
    fun `amber is not available when no routing capability exists`() {
        // The whole bug in one line: without a routing privilege the app cannot claim media
        // has moved, no matter what the user tapped.
        assertEquals(false, Claim.canMoveMedia(routingWorks = false))
        assertTrue(Claim.canMoveMedia(routingWorks = true))
    }

    @Test
    fun `the headline says calls only rather than something vague`() {
        assertEquals(
            "Calls only — media follows the system",
            Claim.headline(shellRunning = true, shellAllowed = true, routingWorks = false),
        )
        assertEquals(
            "Routing media",
            Claim.headline(shellRunning = true, shellAllowed = true, routingWorks = true),
        )
    }

    @Test
    fun `shizuku problems outrank routing in the headline`() {
        // If the shell is down, saying "calls only" hides the actual reason and sends you
        // looking in the wrong place.
        assertEquals(
            "Shizuku is not running",
            Claim.headline(shellRunning = false, shellAllowed = true, routingWorks = false),
        )
        assertEquals(
            "Shizuku has not been allowed",
            Claim.headline(shellRunning = true, shellAllowed = false, routingWorks = true),
        )
    }

    @Test
    fun `an earpiece is never a music destination`() {
        assertEquals(false, Claim.carriesMedia(AudioType.EARPIECE))
        assertEquals(false, Claim.carriesMedia(AudioType.TELEPHONY))
        assertTrue(Claim.carriesMedia(AudioType.SPEAKER))
        assertTrue(Claim.carriesMedia(AudioType.BLUETOOTH_A2DP))
        assertTrue(Claim.carriesMedia(AudioType.WIRED_HEADPHONES))
    }

    @Test
    fun `a row that cannot carry music says so, in words not colour`() {
        assertEquals("  calls only", Claim.annotation(carriesMedia = false, holdsCallRoute = false))
        assertEquals("  calls only, in use", Claim.annotation(carriesMedia = false, holdsCallRoute = true))
        assertEquals("  calls here", Claim.annotation(carriesMedia = true, holdsCallRoute = true))
        assertEquals("", Claim.annotation(carriesMedia = true, holdsCallRoute = false))
    }
}

/** Chip labels for the one-row collapsed panel. */
class ChipTest {

    @Test
    fun `a short label is left alone`() {
        assertEquals("Earpiece", Chip.short("Earpiece"))
        assertEquals("Mono", Chip.short("Mono"))
    }

    @Test
    fun `a long label falls back to its first whole word, not a truncation`() {
        // "Wired headphones" -> "Wired", never "Wired hea…". §10: shorten by rule.
        assertEquals("Wired", Chip.short("Wired headphones"))
        // "Speaker" is short enough to survive whole now, which is half the reason for the
        // rename: "Phone speaker" was being cut to "Phone", and the phone is the whole object.
        assertEquals("Speaker", Chip.short("Speaker"))
    }

    @Test
    fun `a long single word is truncated with an ellipsis as the last resort`() {
        assertEquals("Supercali…", Chip.short("Supercalifragilistic"))
    }

    @Test
    fun `the boundary is inclusive at both ends`() {
        assertEquals("1234567890", Chip.short("1234567890"))
        assertEquals("Ab", Chip.short("Ab cdefghijklmno"))
    }

    @Test
    fun `an empty label does not crash or produce a bare ellipsis`() {
        assertEquals("", Chip.short(""))
        assertEquals("", Chip.short("   "))
    }
}

/**
 * The patch bay. Two paths, N destinations, and every crosspoint has to be honest about
 * whether it can be made.
 */
class PatchBayTest {

    @Test
    fun `media to an earpiece is blocked and stays blocked even with full privilege`() {
        // The app's longest-standing lie: offering the earpiece as a music destination.
        assertEquals(
            Cell.BLOCKED,
            PatchBay.cell(AudioType.EARPIECE, routingWorks = true, isCurrent = false),
        )
        assertEquals(
            Cell.BLOCKED,
            PatchBay.cell(AudioType.EARPIECE, routingWorks = true, isCurrent = true),
        )
    }

    

    

    

    @Test
    fun `every cell state has a distinct mark, so colour is not the only channel`() {
        val marks = Cell.values().map { PatchBay.mark(it) }
        assertEquals(marks.size, marks.toSet().size)
        assertEquals("\u25CF", PatchBay.mark(Cell.CONNECTED))
        assertEquals("\u25CB", PatchBay.mark(Cell.CONNECTABLE))
    }

    
}

/**
 * Volume, per stream. The gap that made a correctly-routed call inaudible.
 */
class VolumeTest {

    @Test
    fun `the top of the slider reaches the top of the range`() {
        // Truncation puts 99% at 4 of 5 and the slider can never reach maximum. Rounding fixes it.
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
        assertTrue(Volume.isLow(0, 15))
        assertEquals(false, Volume.isLow(8, 15))
        // The boundary itself, both sides.
        assertTrue(Volume.isLow(25, 100))
        assertEquals(false, Volume.isLow(26, 100))
    }

    @Test
    fun `the label states the real numbers, not a percentage`() {
        assertEquals("Call  3 / 15", Volume.label("Call", 3, 15))
    }

    @Test
    fun `the shell fallback names the stream by its platform id`() {
        assertEquals("media volume --stream 0 --set 9", Volume.shellCommand(0, 9))
    }
}

/** Press feedback. A control that goes blank on press is the bug being fixed. */
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
 * The half / full tile toggle.
 */
class VolumeToggleTest {

    @Test
    fun `full goes to half and half goes back to full`() {
        assertEquals(8, VolumeToggle.target(15, 15))   // 100% -> 50%
        assertEquals(15, VolumeToggle.target(8, 15))   // 53%  -> 100%
        assertEquals(8, VolumeToggle.target(16, 16))
        assertEquals(16, VolumeToggle.target(8, 16))
    }

    @Test
    fun `two presses return you to where you started`() {
        // The property that rules out the naive `if (index == max) half else max`.
        for (max in listOf(5, 7, 15, 16, 30)) {
            val half = Volume.indexFor(50, max)
            val once = VolumeToggle.target(half, max)
            val twice = VolumeToggle.target(once, max)
            assertEquals("max=$max", half, twice)
        }
    }

    @Test
    fun `a stream that is loud but not maximum still quietens`() {
        // The case the whole LOUD_FROM_PERCENT rule exists for, and the one the first version
        // of this suite missed: 13/15 is 87%, loud but not at the top. The naive
        // `if (index == max)` toggle raises it to 100% instead of halving it, and every other
        // test in this class passes with that bug in place.
        assertTrue(VolumeToggle.atFull(13, 15))
        assertEquals(8, VolumeToggle.target(13, 15))
        assertEquals(8, VolumeToggle.target(14, 15))
        // and just below the line, it goes up
        assertEquals(false, VolumeToggle.atFull(11, 15))
        assertEquals(15, VolumeToggle.target(11, 15))
    }

    @Test
    fun `a quiet stream goes loud first, from any starting level`() {
        assertEquals(15, VolumeToggle.target(0, 15))
        assertEquals(15, VolumeToggle.target(1, 15))
        assertEquals(15, VolumeToggle.target(4, 15))
    }

    @Test
    fun `the loud boundary is exact on both sides`() {
        // 75% counts as loud; anything under it does not.
        assertTrue(VolumeToggle.atFull(75, 100))
        assertEquals(false, VolumeToggle.atFull(74, 100))
    }

    @Test
    fun `a stream with no range is neither full nor toggled`() {
        assertEquals(false, VolumeToggle.atFull(0, 0))
        assertEquals(0, VolumeToggle.target(0, 0))
        assertEquals("unavailable", VolumeToggle.subtitle(3, 0))
    }

    @Test
    fun `the subtitle answers without a press`() {
        assertEquals("100%  (15/15)", VolumeToggle.subtitle(15, 15))
        assertEquals("53%  (8/15)", VolumeToggle.subtitle(8, 15))
    }

    @Test
    fun `streams are found by platform id, not by list position`() {
        assertEquals("Call", Volume.byId(0).label)
        assertEquals("Media", Volume.byId(3).label)   // renamed in v15 to match the system panel
        assertEquals("Ring", Volume.byId(2).label)
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

/**
 * What a tile says when the panel gives it no room to speak.
 */
class TileTextTest {

    @Test
    fun `the badge is the percentage, short enough for a tile`() {
        assertEquals("100%", TileText.badge(15, 15))
        assertEquals("50%", TileText.badge(8, 16))
        assertTrue(TileText.badge(8, 15).length <= 4)
    }

    @Test
    fun `a stream with no range says so rather than showing zero percent`() {
        // "0%" would be a lie: the stream has no range, it is not silent.
        assertEquals("--", TileText.badge(0, 0))
        assertEquals("Call unavailable", TileText.label("Call", 0, 0))
    }

    @Test
    fun `the label carries the name and the level together`() {
        assertEquals("Media 100%", TileText.label("Media", 16, 16))
        assertEquals("Alarm 50%", TileText.label("Alarm", 8, 16))
    }

    @Test
    fun `the subtitle says what the next press does, not what the state is`() {
        assertEquals("tap for 50%", TileText.nextAction(15, 15))
        assertEquals("tap for 100%", TileText.nextAction(4, 15))
        assertEquals("no range on this device", TileText.nextAction(0, 0))
    }

    @Test
    fun `every stream in the panel has a tile, and the names match the platform`() {
        // "Media" not "Music": the system volume panel says Media, and borrowing its
        // vocabulary means nothing has to be translated by the person reading it.
        assertEquals(listOf("Call", "Media", "Ring", "Notification", "Alarm"),
            Volume.STREAMS.map { it.label })
        listOf(0, 3, 2, 5, 4).forEach { Volume.byId(it) }
    }
}
