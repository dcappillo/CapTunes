package app.captunes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.Collections
import kotlin.math.abs
import kotlin.math.log10

/**
 * How popular each song is around the world, from Deezer's public catalog
 * (its "rank": 0 to 1,000,000, updated daily from streams; no account needed).
 *
 * Lookups run quietly in the background, about 7 per second (Deezer's limit
 * is 10), and are cached on the phone for 90 days. Mixes use whatever is cached and get
 * sharper as more songs are looked up. Only title, artist, album and length are sent.
 */
object Popularity {
    @Serializable private data class Artist(val name: String = "")
    @Serializable private data class Hit(val title: String = "", val duration: Int = 0, val rank: Int = 0, val artist: Artist = Artist())
    @Serializable private data class Result(val data: List<Hit> = emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Track>(capacity = 2000)
    private val pending: MutableSet<String> = Collections.synchronizedSet(HashSet())
    @Volatile private var log: PlayLog? = null

    /** Songs not found on Deezer get rank 0 so they aren't looked up again for 90 days. */
    fun start(playLog: PlayLog) {
        if (log != null) return
        log = playLog
        scope.launch {
            for (track in queue) {
                // Checked again here in case the switch was turned off while songs were waiting.
                if (Lookups.enabled) {
                    runCatching { lookup(track) }.onSuccess { rank -> playLog.saveRank(track.id, rank ?: 0) }
                }
                pending -= track.id
                delay(140)
            }
        }
    }

    /** Queue songs that have no cached rank yet. */
    fun request(tracks: Collection<Track>, known: Set<String>) {
        if (!Lookups.enabled) return
        for (t in tracks) {
            if (t.id in known || !pending.add(t.id)) continue
            if (queue.trySend(t).isFailure) { pending -= t.id; break }
        }
    }

    /**
     * Popularity as a score from -1 (obscure) to +1 (a massive hit).
     * Deezer ranks are roughly logarithmic: ~1,000 is niche, ~30,000 middling, ~1,000,000 huge.
     */
    fun score(rank: Int?): Double {
        if (rank == null || rank <= 0) return 0.0
        return ((log10(rank.toDouble()) - 4.5) / 1.5).coerceIn(-1.0, 1.0)
    }

    private fun lookup(track: Track): Int? {
        val title = clean(track.title)
        val url = "https://api.deezer.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "artist:\"${track.artist}\" track:\"$title\"")
            .addQueryParameter("limit", "5")
            .build()
        val body = Net.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        } ?: return null
        val hits = Net.json.decodeFromString(Result.serializer(), body).data
        if (hits.isEmpty()) return null
        val seconds = (track.durationMs / 1000).toInt()
        // Prefer the same length (same recording); otherwise the best-known match.
        val sameLength = hits.filter { seconds == 0 || abs(it.duration - seconds) <= 5 }
        return (sameLength.ifEmpty { hits }).maxOf { it.rank }
    }

    /** "Red (Taylor's Version) - Remastered 2009" → "Red (Taylor's Version)": drops remaster/edit tags. */
    private fun clean(title: String): String =
        title.replace(Regex("""\s*[-(\[]\s*(\d{4}\s*)?(remaster(ed)?|mono|stereo|single version|radio edit|bonus track)[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { title }
}
