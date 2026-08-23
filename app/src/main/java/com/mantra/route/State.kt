package com.mantra.route

import android.content.Context

/**
 * What survives a reboot.
 *
 * The probe verdicts are cached because probing writes to secure settings, and doing that
 * every time the notification is drawn would mean the phone briefly going mono each time
 * anybody glanced at it.
 */
class State(context: Context) {

    private val prefs = context.getSharedPreferences("mantra_route", Context.MODE_PRIVATE)

    /**
     * The stable key of the last chosen output, not its id. §7's neighbour problem: an id is
     * reissued on every reconnection, so remembering one means forgetting the choice the
     * moment the headset is unplugged.
     */
    var lastSelectedKey: String
        get() = prefs.getString(KEY_SELECTED_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SELECTED_KEY, value).apply()

    /**
     * design-language.md §7: what is switched OFF, never what is on.
     *
     * An output kind that Android adds next year is therefore live by default rather than
     * invisible to anyone whose saved list predates it.
     */
    var switchedOff: Set<String>
        get() = prefs.getStringSet(KEY_OFF, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_OFF, value).apply()

    fun toggleOff(key: String) {
        switchedOff = switchedOff.toMutableSet().also { if (!it.remove(key)) it.add(key) }
    }

    /** §7: one button to put it back the way it shipped. */
    fun resetArrangement() {
        switchedOff = emptySet()
    }

    var blend: Blend
        get() = runCatching { Blend.valueOf(prefs.getString(KEY_BLEND, null) ?: "") }
            .getOrDefault(Blend.STEREO)
        set(value) = prefs.edit().putString(KEY_BLEND, value.name).apply()

    var balance: Float
        get() = prefs.getFloat(KEY_BALANCE, 0f)
        set(value) = prefs.edit().putFloat(KEY_BALANCE, BlendMath.clampBalance(value)).apply()

    var lastProbeAt: Long
        get() = prefs.getLong(KEY_PROBED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PROBED_AT, value).apply()

    fun saveCapabilities(caps: Capabilities) {
        val editor = prefs.edit()
        caps.results.forEach { r ->
            editor.putString("$KEY_CAP${r.id}", r.verdict.name)
            editor.putString("$KEY_DETAIL${r.id}", r.detail)
            editor.putString("$KEY_TITLE${r.id}", r.title)
            editor.putString("$KEY_BUYS${r.id}", r.buys)
        }
        editor.putStringSet(KEY_CAP_IDS, caps.results.map { it.id }.toSet())
        // Stamp the build. v4 opened showing v3's stored verdicts — including two strings v4
        // was written to stop producing — because SharedPreferences survive an upgrade and
        // nothing invalidated them.
        editor.putInt(KEY_CAP_VERSION, BuildConfig.VERSION_CODE)
        editor.apply()
        lastProbeAt = System.currentTimeMillis()
    }

    /**
     * Reading back a probe that was never run gives UNTESTED, not WORKS.
     *
     * modules/four-tests.md: a check that finds nothing and a check that runs nothing look
     * identical from outside. The default here has to be the pessimistic one, or the first
     * launch behaves as though every capability had passed.
     */
    fun capabilities(): Capabilities {
        // A verdict measured by a different build is a verdict about different code.
        if (prefs.getInt(KEY_CAP_VERSION, -1) != BuildConfig.VERSION_CODE) {
            return Capabilities(emptyList())
        }
        val ids = prefs.getStringSet(KEY_CAP_IDS, emptySet()).orEmpty()
        val results = ids.map { id ->
            ProbeResult(
                id = id,
                title = prefs.getString("$KEY_TITLE$id", id).orEmpty(),
                buys = prefs.getString("$KEY_BUYS$id", "").orEmpty(),
                verdict = runCatching {
                    Verdict.valueOf(prefs.getString("$KEY_CAP$id", null) ?: "")
                }.getOrDefault(Verdict.UNTESTED),
                detail = prefs.getString("$KEY_DETAIL$id", "").orEmpty(),
            )
        }
        return Capabilities(results)
    }

    private companion object {
        const val KEY_SELECTED_KEY = "selected_key"
        const val KEY_OFF = "switched_off"
        const val KEY_BLEND = "blend"
        const val KEY_BALANCE = "balance"
        const val KEY_PROBED_AT = "probed_at"
        const val KEY_CAP_IDS = "cap_ids"
        const val KEY_CAP_VERSION = "cap_version"
        const val KEY_CAP = "cap_"
        const val KEY_DETAIL = "cap_detail_"
        const val KEY_TITLE = "cap_title_"
        const val KEY_BUYS = "cap_buys_"
    }
}
