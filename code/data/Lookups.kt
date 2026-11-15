package app.captunes.data

import android.content.Context

/**
 * The switch for the two online lookups: lyrics from LRCLIB and world popularity
 * from Deezer. Both send a song's title, artist, album and length to those services,
 * so they stay off until you turn them on.
 */
object Lookups {
    private const val PREFS = "captunes_settings"
    private const val KEY = "onlineLookups"
    private const val KEY_ASKED = "onlineLookupsAsked"

    /** True once you've said yes. Nothing is sent to LRCLIB or Deezer while this is false. */
    @Volatile var enabled = false
        private set

    /** True once you've answered the question, either way, so it's only asked once. */
    @Volatile var asked = false
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean(KEY, false)
        asked = p.getBoolean(KEY_ASKED, false)
    }

    fun set(context: Context, on: Boolean) {
        enabled = on
        asked = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, on).putBoolean(KEY_ASKED, true).apply()
    }
}
