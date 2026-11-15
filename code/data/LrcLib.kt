package app.captunes.data

import app.captunes.util.guarded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.abs

/**
 * Timed lyrics from LRCLIB (lrclib.net), a free open lyrics database.
 * Used when Navidrome has no synced lyrics for a song. Sends only the
 * song's title, artist, album and length.
 */
object LrcLib {
    @Serializable
    private data class Entry(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "CapTunes (open-source music player)"

    suspend fun find(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val seconds = (track.durationMs / 1000).toInt()
        val exact = guarded("LrcLib") {
            get(
                "$BASE/get".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", track.title)
                    .addQueryParameter("artist_name", track.artist)
                    .addQueryParameter("album_name", track.album)
                    .addQueryParameter("duration", seconds.toString())
                    .build().toString(),
            )?.let { Net.json.decodeFromString(Entry.serializer(), it) }
        }.getOrNull()
        val entry = exact?.takeIf { !it.syncedLyrics.isNullOrBlank() } ?: search(track, seconds) ?: exact
        entry?.toLyrics()
    }

    /** Fuzzy fallback: same title and artist, length within 3 seconds, synced preferred. */
    private fun search(track: Track, seconds: Int): Entry? = guarded("LrcLib") {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .build().toString()
        val body = get(url) ?: return null
        val results = Net.json.decodeFromString(ListSerializer(Entry.serializer()), body)
        val close = results.filter { e -> e.duration == null || abs(e.duration - seconds) <= 3 }
        close.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: close.firstOrNull { !it.plainLyrics.isNullOrBlank() }
    }.getOrNull()

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
        return Net.http.newCall(request).execute().use { r -> if (r.isSuccessful) Net.text(r, Net.MAX_LOOKUP_REPLY) else null }
    }

    private fun Entry.toLyrics(): Lyrics? {
        if (instrumental) return Lyrics(synced = false, lines = listOf(LyricLine(null, "♪ Instrumental ♪")), source = "LRCLIB")
        syncedLyrics?.takeIf { it.isNotBlank() }?.let { lrc ->
            val lines = parseLrc(lrc)
            if (lines.isNotEmpty()) return Lyrics(synced = true, lines = lines, source = "LRCLIB")
        }
        return plainLyrics?.takeIf { it.isNotBlank() }
            ?.let { Lyrics(false, it.lines().map { l -> LyricLine(null, l) }, source = "LRCLIB") }
    }

    /** Parses "[mm:ss.xx] text" lines; a line may carry several timestamps. */
    fun parseLrc(lrc: String): List<LyricLine> = Lrc.parse(lrc).map { (ms, text) -> LyricLine(ms, text) }
}
