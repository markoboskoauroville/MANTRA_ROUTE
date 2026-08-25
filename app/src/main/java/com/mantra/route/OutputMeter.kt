package com.mantra.route

import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer

/**
 * The output mix: metering it, and boosting it.
 *
 * THERE IS ONE MIX, NOT FIVE. `Visualizer` may attach to the global output session (0) or to a
 * session this app owns, and to nothing else. It cannot attach to Tidal's session, so it cannot
 * tell which of the five streams a sample came from. Five meters would be one number drawn five
 * times, and `LoudnessEnhancer` on session 0 is likewise one gain applied once, globally.
 *
 * So there is one meter, one normalise and one boost, in a section that says "output mix" —
 * rather than five of each, four of which would be lying about being independent.
 *
 * NOT VERIFIED ON A DEVICE. Both effects attach to session 0, which needs RECORD_AUDIO and,
 * since Android 9, the app in the foreground. Some builds refuse it outright. Every failure
 * here is reported by name to the screen rather than leaving a meter that simply never moves —
 * a dead meter and a silent room look identical.
 */
class OutputMeter {

    /** Global output mix. */
    private companion object { const val SESSION_GLOBAL = 0 }

    private var visualizer: Visualizer? = null
    private var enhancer: LoudnessEnhancer? = null

    var lastError: String? = null
        private set

    /** Peak of the most recent capture, in dBFS. Floor when nothing is playing. */
    @Volatile
    var peakDb: Float = Meter.FLOOR_DB
        private set

    val isMetering: Boolean get() = visualizer != null

    fun start(): Boolean {
        if (visualizer != null) return true
        return try {
            val v = Visualizer(SESSION_GLOBAL)
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        who: Visualizer?, waveform: ByteArray?, rate: Int
                    ) {
                        waveform ?: return
                        peakDb = Meter.toDb(peakOf(waveform))
                    }

                    override fun onFftDataCapture(who: Visualizer?, fft: ByteArray?, rate: Int) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false,
            )
            v.enabled = true
            visualizer = v
            lastError = null
            true
        } catch (t: Throwable) {
            lastError = (t.cause ?: t).javaClass.simpleName + ": " + (t.message ?: "refused")
            release()
            false
        }
    }

    /**
     * Waveform bytes are UNSIGNED, centred on 128. Reading them as signed puts silence at -128
     * and pins the meter at full scale, which is the classic way this looks like it works.
     */
    private fun peakOf(waveform: ByteArray): Float {
        var peak = 0
        for (b in waveform) {
            val centred = kotlin.math.abs((b.toInt() and 0xFF) - 128)
            if (centred > peak) peak = centred
        }
        return peak / 128f
    }

    /** Apply gain to the whole output. 0 removes it. */
    fun setGainMb(gainMb: Int): String? = try {
        if (gainMb <= 0) {
            enhancer?.enabled = false
            null
        } else {
            val e = enhancer ?: LoudnessEnhancer(SESSION_GLOBAL).also { enhancer = it }
            e.setTargetGain(gainMb)
            e.enabled = true
            null
        }
    } catch (t: Throwable) {
        val why = (t.cause ?: t).javaClass.simpleName + ": " + (t.message ?: "refused")
        lastError = why
        why
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.release() }
        enhancer = null
        peakDb = Meter.FLOOR_DB
    }
}
