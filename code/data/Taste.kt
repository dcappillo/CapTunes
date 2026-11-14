package app.captunes.data

import kotlin.math.abs
import kotlin.math.pow

/**
 * What Cap Tunes has learned about your taste from the play log.
 *
 * Every play adds a signal to its song: finished +1, partial +0.2,
 * late skip -0.4, early skip -1, "Not for me" -5. Signals fade with a
 * 30-day half-life, so recent behavior matters most. Song scores roll up
 * into artist and genre affinities, and artists you play in the same
 * sitting become "neighbors".
 */
class TasteProfile(
    val songScore: Map<String, Double>,
    val artistScore: Map<String, Double>,   // key: artist name, lowercased; normalized to [-1, 1]
    val genreScore: Map<String, Double>,    // key: genre, lowercased; normalized to [-1, 1]
    val neighbors: Map<String, Map<String, Double>>,
    val lastPlayed: Map<String, Long>,
    val recentPlays: Map<String, Int>,      // completed plays in the last 30 days
    val lifetimeLikes: Map<String, Int>,    // completed plays, all time
    val disliked: Set<String>,
    val known: Map<String, Track>,
    val totalPlays: Int,
) {
    fun artistAffinity(artist: String) = artistScore[artist.lowercase()] ?: 0.0
    fun genreAffinity(genre: String?) = genre?.let { genreScore[it.lowercase()] } ?: 0.0

    /** How much you'd probably like [t]; roughly -1..+2. */
    fun predict(t: Track): Double {
        if (t.id in disliked) return -10.0
        val artist = artistAffinity(t.artist)
        val genre = genreAffinity(t.genre)
        val neighborBoost = neighbors[t.artist.lowercase()].orEmpty().entries
            .sumOf { (other, weight) -> weight * (artistScore[other] ?: 0.0) }
            .coerceIn(-1.0, 1.0)
        val own = songScore[t.id]
        return if (own != null) own.coerceIn(-3.0, 3.0) / 3 + 0.4 * artist + 0.2 * genre
        else 0.6 * artist + 0.3 * genre + 0.3 * neighborBoost
    }

    val topArtists: List<String> get() = artistScore.entries.filter { it.value > 0 }
        .sortedByDescending { it.value }.map { it.key }

    val topGenres: List<String> get() = genreScore.entries.filter { it.value > 0 }
        .sortedByDescending { it.value }.map { it.key }
}

object Taste {
    private const val DAY = 86_400_000L
    private const val SESSION_GAP = 30 * 60_000L

    fun weight(o: Outcome) = when (o) {
        Outcome.Complete -> 1.0
        Outcome.Partial -> 0.2
        Outcome.LateSkip -> -0.4
        Outcome.EarlySkip -> -1.0
        Outcome.Dislike -> -5.0
    }

    fun build(rows: List<PlayRow>, now: Long = System.currentTimeMillis()): TasteProfile {
        val song = HashMap<String, Double>()
        val artist = HashMap<String, Double>()
        val genre = HashMap<String, Double>()
        val last = HashMap<String, Long>()
        val recent = HashMap<String, Int>()
        val lifetime = HashMap<String, Int>()
        val disliked = HashSet<String>()
        val known = HashMap<String, Track>()

        for (r in rows) {
            val id = r.track.id
            known[id] = r.track
            if (r.outcome == Outcome.Dislike) disliked += id
            val ageDays = (now - r.startedAt).coerceAtLeast(0).toDouble() / DAY
            val w = weight(r.outcome) * 0.5.pow(ageDays / 30)
            song.merge(id, w, { x, y -> x + y })
            artist.merge(r.track.artist.lowercase(), w, { x, y -> x + y })
            r.track.genre?.takeIf { it.isNotBlank() }?.let { genre.merge(it.lowercase(), w, { x, y -> x + y }) }
            if (r.outcome != Outcome.Dislike) last[id] = maxOf(last[id] ?: 0, r.startedAt)
            if (r.outcome == Outcome.Complete) {
                lifetime.merge(id, 1, { x, y -> x + y })
                if (now - r.startedAt < 30 * DAY) recent.merge(id, 1, { x, y -> x + y })
            }
        }
        // An explicit "Not for me" wins even if you finished the song before.
        disliked.forEach { song[it] = -5.0 }

        return TasteProfile(
            songScore = song,
            artistScore = normalize(artist),
            genreScore = normalize(genre),
            neighbors = neighbors(rows),
            lastPlayed = last,
            recentPlays = recent,
            lifetimeLikes = lifetime,
            disliked = disliked,
            known = known,
            totalPlays = rows.count { it.outcome != Outcome.Dislike },
        )
    }

    private fun normalize(m: Map<String, Double>): Map<String, Double> {
        val max = m.values.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: return m
        return m.mapValues { it.value / max }
    }

    /** Artists you enjoy in the same listening session, weighted 0..1. */
    private fun neighbors(rows: List<PlayRow>): Map<String, Map<String, Double>> {
        val pairs = HashMap<String, HashMap<String, Double>>()
        var session = HashSet<String>()
        var lastTime = 0L
        fun flush() {
            val list = session.toList()
            for (a in list) for (b in list) if (a != b) {
                pairs.getOrPut(a) { HashMap() }.merge(b, 1.0, { x, y -> x + y })
            }
            session = HashSet()
        }
        for (r in rows) {
            if (r.startedAt - lastTime > SESSION_GAP) flush()
            lastTime = r.startedAt
            if (r.outcome == Outcome.Complete) session += r.track.artist.lowercase()
        }
        flush()
        return pairs.mapValues { (_, m) -> normalize(m) }
    }
}
