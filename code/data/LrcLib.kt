package app.captunes.data

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
        val exact = runCatching {
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
    private fun search(track: Track, seconds: Int): Entry? = runCatching {
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
        return Net.http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
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

    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses "[mm:ss.xx] text" lines; a line may carry several timestamps. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val (mm, ss, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                out += LyricLine(mm.toLong() * 60_000 + ss.toLong() * 1000 + fracMs, text)
            }
        }
        return out.sortedBy { it.startMs }
    }
}
