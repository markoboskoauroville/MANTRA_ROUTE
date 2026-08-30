package com.mantra.route

/**
 * Colour schemes.
 *
 * All ten are built from the same five roles as the original, not invented freehand: a near
 * black GROUND, a warm INK for text, one ACCENT that carries every "in force" state, a SURFACE
 * for buttons and empty track, and a MUTED tone for text that is present but secondary.
 *
 * Every scheme is measured rather than eyeballed — `contrast` is real WCAG arithmetic and
 * `worstContrast` reports the weakest pairing in a scheme, so a pretty combination that cannot
 * be read fails a test instead of shipping.
 */
data class Scheme(
    val name: String,
    val ground: Int,
    val ink: Int,
    val accent: Int,
    val surface: Int,
    val muted: Int,
    val progress: Int,
)

object Schemes {

    val ALL = listOf(
        // The original. Every other scheme is this one's structure in a different key.
        Scheme("Sunrise", 0xFF0B0D10.toInt(), 0xFFF2DDB4.toInt(), 0xFFF59E0B.toInt(),
            0xFF23303D.toInt(), 0xFF8FA3B5.toInt(), 0xFF3E6B63.toInt()),
        Scheme("Ember", 0xFF120A0A.toInt(), 0xFFF6D9C8.toInt(), 0xFFE2542B.toInt(),
            0xFF33211C.toInt(), 0xFFB08D82.toInt(), 0xFF7A3B22.toInt()),
        Scheme("Moss", 0xFF0A0F0B.toInt(), 0xFFDCE9CE.toInt(), 0xFF7FB847.toInt(),
            0xFF1F2C21.toInt(), 0xFF93A88C.toInt(), 0xFF3D6B3A.toInt()),
        Scheme("Indigo", 0xFF090B14.toInt(), 0xFFD9DEF5.toInt(), 0xFF7C8CF8.toInt(),
            0xFF1E2338.toInt(), 0xFF8E96BE.toInt(), 0xFF3A4482.toInt()),
        Scheme("Rose", 0xFF120A0F.toInt(), 0xFFF6D6E4.toInt(), 0xFFEC6A9C.toInt(),
            0xFF32202B.toInt(), 0xFFB68CA0.toInt(), 0xFF7A3554.toInt()),
        Scheme("Ice", 0xFF061014.toInt(), 0xFFD5ECF5.toInt(), 0xFF4FC3E8.toInt(),
            0xFF162932.toInt(), 0xFF87A5B2.toInt(), 0xFF2C6478.toInt()),
        Scheme("Plum", 0xFF0E0912.toInt(), 0xFFE6D8F2.toInt(), 0xFFB07CF0.toInt(),
            0xFF261B33.toInt(), 0xFF9C8CB0.toInt(), 0xFF553A75.toInt()),
        Scheme("Ochre", 0xFF100C06.toInt(), 0xFFF0E2C4.toInt(), 0xFFD9A441.toInt(),
            0xFF2C2417.toInt(), 0xFFA9987A.toInt(), 0xFF6B5426.toInt()),
        Scheme("Teal", 0xFF06110F.toInt(), 0xFFD2EDE5.toInt(), 0xFF3FCBA8.toInt(),
            0xFF152B27.toInt(), 0xFF83A79E.toInt(), 0xFF2A6A5B.toInt()),
        Scheme("Ash", 0xFF0D0D0E.toInt(), 0xFFE4E4E6.toInt(), 0xFFB9BDC4.toInt(),
            0xFF26272A.toInt(), 0xFF9A9CA1.toInt(), 0xFF4E5157.toInt()),
    )

    /** Names are what the swatches announce to a screen reader, so they must not repeat. */
    val names: List<String> get() = ALL.map { it.name }

    fun byIndex(index: Int): Scheme = ALL[index.coerceIn(0, ALL.lastIndex)]

    // ---- measurement ------------------------------------------------------------------------

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(colour: Int): Double =
        0.2126 * channel((colour shr 16) and 0xFF) +
            0.7152 * channel((colour shr 8) and 0xFF) +
            0.0722 * channel(colour and 0xFF)

    /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * The weakest pairing that actually appears on screen.
     *
     * Only pairings that are really drawn together are checked. Measuring every colour against
     * every other would fail schemes for combinations that never meet — and would tempt me to
     * flatten the palette until the arithmetic passed, which is how a scheme loses its
     * character without gaining a reader.
     */
    fun worstContrast(scheme: Scheme): Double = listOf(
        contrast(scheme.ink, scheme.ground),        // labels
        contrast(scheme.accent, scheme.ground),     // thumb and progress against the page
        contrast(scheme.ink, scheme.surface),       // button text
        contrast(scheme.muted, scheme.ground),      // secondary text
        contrast(scheme.ground, scheme.accent),     // the number inside the thumb
    ).min()
}
