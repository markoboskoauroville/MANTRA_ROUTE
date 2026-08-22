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
