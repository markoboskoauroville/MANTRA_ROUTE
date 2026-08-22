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
        assertEquals(listOf("Phone speaker", "Earpiece"), rows.map { it.label })
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
        assertEquals("Phone speaker", rows.first().label)
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
            listOf("Phone speaker", "Wired headphones", "Ear"),
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
        assertEquals("Phone speaker", Outputs.labelFor(AudioType.SPEAKER, "Nothing Phone (2a)"))
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
        assertEquals(listOf("Phone speaker"), visible.map { it.label })
    }

    /**
     * The whole reason §7 says store the exclusions. If the set held what to SHOW, an output
     * kind that did not exist when the set was saved would be missing and there would be no
     * way to discover that it should be there.
     */
    @Test
    fun `an output never seen before is shown, because the set holds exclusions`() {
        val rows = Outputs.rows(listOf(out(1, AudioType.SPEAKER), out(2, 99, "Future Thing")))
        val off = setOf(Outputs.keyOf(AudioType.SPEAKER, "Phone speaker"))
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
    fun `swap has no balance value, because balance pans and cannot swap`() {
        assertNull(BlendMath.balanceFor(Blend.SWAPPED))
    }

    @Test
    fun `stereo and mono both sit centred, they differ only in the downmix`() {
        assertEquals(0.0f, BlendMath.balanceFor(Blend.STEREO)!!, 0.0001f)
        assertEquals(0.0f, BlendMath.balanceFor(Blend.MONO)!!, 0.0001f)
        assertEquals(0, BlendMath.monoFor(Blend.STEREO))
        assertEquals(1, BlendMath.monoFor(Blend.MONO))
    }

    @Test
    fun `balance is clamped at both ends and at the ends exactly`() {
        assertEquals(-1.0f, BlendMath.clampBalance(-4f), 0.0001f)
        assertEquals(1.0f, BlendMath.clampBalance(4f), 0.0001f)
        assertEquals(-1.0f, BlendMath.clampBalance(-1.0f), 0.0001f)
        assertEquals(1.0f, BlendMath.clampBalance(1.0f), 0.0001f)
        assertEquals(0.0f, BlendMath.clampBalance(0f), 0.0001f)
    }

    @Test
    fun `swap is refused everywhere, not only in the maths`() {
        // Guards against the failure where balanceFor returns null correctly and some caller
        // treats null as "use the default" and pans to centre while reporting a swap.
        assertTrue(Blend.values().count { BlendMath.balanceFor(it) == null } == 1)
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
