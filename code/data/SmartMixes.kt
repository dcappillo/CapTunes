package app.captunes.data

import app.captunes.util.guarded
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.ln
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** [seed] says how to make more of this mix: "g:genre", "a:artist" or "d:1960". */
class GeneratedMix(val card: Card, val tracks: List<Track>, val seed: String? = null)

/** Last generated mixes, so opening one shows the same songs as its tile. */
object MixStore {
    private val mixes = ConcurrentHashMap<String, GeneratedMix>()
    fun put(all: List<GeneratedMix>) = all.forEach { mixes[it.card.id] = it }
    fun get(id: String): GeneratedMix? = mixes[id]
    fun clear() = mixes.clear()
}

/**
 * Builds personal mixes from the [TasteProfile]:
 * Daily Mixes per favorite genre (or artist circle), On Repeat, Rediscover and Deep Cuts.
 * Each Daily Mix is ~80% songs it's confident you'll like and ~20% exploration.
 */
class SmartMixes(private val source: MusicSource, private val log: PlayLog) {

    /** Per-build context: cached world popularity and the era correction. */
    private class Mixing(val ranks: Map<String, Int>, val era: Map<Int, Double>)

    @Volatile private var libraryDecades: Pair<Long, Map<Int, Int>>? = null

    companion object {
        const val MIN_PLAYS_FOR_DAILY = 40
        /** Daily Mix ids are this plus the mix's seed, e.g. "smart:daily:g:rock". */
        const val DAILY = "smart:daily:"
        private const val MIX_SIZE = 40
        private const val MORE_SIZE = 25
        /** "The 60s", "The 2000s". */
        fun decadeName(d: Int) = "The " + (if (d >= 2000) "${d}s" else "${d % 100}s")
        private const val DAY = 86_400_000L
    }

    suspend fun build(p: TasteProfile, now: Long = System.currentTimeMillis()): List<GeneratedMix> =
        coroutineScope {
            val era = guarded("SmartMixes") { eraBalance(p) }.getOrDefault(emptyMap())
            // Look up world popularity for songs you've played, in the background.
            withContext(Dispatchers.IO) {
                Popularity.request(p.known.values, log.ranks(p.known.keys).keys)
            }
            val ctx = Mixing(emptyMap(), era)
            val daily = async { if (p.totalPlays >= MIN_PLAYS_FOR_DAILY) dailyMixes(p, ctx) else emptyList() }
            val deep = async { guarded("SmartMixes") { deepCuts(p) }.getOrNull() }
            listOfNotNull(onRepeat(p), rediscover(p, now), deep.await()) + daily.await()
        }.also { MixStore.put(it) }

    // ---- Daily Mixes ----

    private suspend fun dailyMixes(p: TasteProfile, ctx: Mixing): List<GeneratedMix> = coroutineScope {
        val genreSeeds = p.topGenres.take(5)
        val artistSeeds = if (genreSeeds.size >= 3) emptyList() else p.topArtists.take(5 - genreSeeds.size)
        val jobs = genreSeeds.map { g -> async { guarded("SmartMixes") { genreMix(p, g, ctx) }.getOrNull() } } +
            artistSeeds.map { a -> async { guarded("SmartMixes") { artistMix(p, a, ctx) }.getOrNull() } }
        jobs.awaitAll().filterNotNull().mapIndexed { i, (seeded, tracks) ->
            val (seed, label) = seeded
            val headliners = tracks.groupingBy { it.artist }.eachCount().entries
                .sortedByDescending { it.value }.take(3).joinToString(", ") { it.key }
            GeneratedMix(
                Card("$DAILY$seed", "Daily Mix ${i + 1}", "$label · $headliners", null, Kind.Mix),
                tracks,
                seed,
            )
        }
    }

    private suspend fun genreMix(
        p: TasteProfile, genreKey: String, ctx: Mixing,
        exclude: Set<String> = emptySet(), size: Int = MIX_SIZE,
    ): Pair<Pair<String, String>, List<Track>>? {
        val serverGenre = p.known.values.firstOrNull { it.genre?.lowercase() == genreKey }?.genre ?: genreKey
        val fromServer = source.randomSongs(300, serverGenre)
        val fromLog = p.known.values.filter { it.genre?.lowercase() == genreKey }
        val picked = pick(p, (fromServer + fromLog).distinctBy { it.id }, ctx, exclude, size) ?: return null
        return ("g:$genreKey" to serverGenre) to picked
    }

    /** A decade mix: your favorites from that decade, its best-known songs, and some exploring. */
    suspend fun decadeMix(p: TasteProfile, decade: Int, exclude: Set<String> = emptySet(), size: Int = 50): List<Track>? {
        val fromServer = source.randomSongs(300, null, decade, decade + 9)
        val fromLog = p.known.values.filter { (it.year ?: 0) in decade..decade + 9 }
        return pick(p, (fromServer + fromLog).distinctBy { it.id }, Mixing(emptyMap(), emptyMap()), exclude, size, minimum = 5)
    }

    /** Builds (and remembers) the mix for "decade:1960". */
    suspend fun decade(p: TasteProfile, decade: Int): GeneratedMix? {
        val tracks = decadeMix(p, decade) ?: return null
        return GeneratedMix(
            Card("decade:$decade", decadeName(decade), "Your $decade–${decade + 9} music", null, Kind.Mix),
            tracks, "d:$decade",
        ).also { MixStore.put(listOf(it)) }
    }

    /**
     * Fresh songs to keep a mix going once you near the end of it,
     * skipping anything already in the queue. Empty for mixes that are finite by design.
     */
    suspend fun more(id: String, p: TasteProfile, exclude: Set<String>): List<Track> {
        // A Daily Mix's id carries its seed, so it always gets more of the same mix
        // even after Home has rebuilt and renumbered the mixes.
        val seed = id.takeIf { it.startsWith(DAILY) }?.removePrefix(DAILY)?.takeIf { it.length > 2 }
            ?: id.removePrefix("decade:").toIntOrNull()?.takeIf { id.startsWith("decade:") }?.let { "d:$it" }
            ?: MixStore.get(id)?.seed
            ?: return emptyList()
        val key = seed.substring(2)
        return when (seed[0]) {
            'd' -> decadeMix(p, key.toInt(), exclude, MORE_SIZE)
            'g' -> genreMix(p, key, Mixing(emptyMap(), eraOrEmpty(p)), exclude, MORE_SIZE)?.second
            'a' -> artistMix(p, key, Mixing(emptyMap(), eraOrEmpty(p)), exclude, MORE_SIZE)?.second
            else -> null
        }.orEmpty()
    }

    private suspend fun eraOrEmpty(p: TasteProfile) = guarded("SmartMixes") { eraBalance(p) }.getOrDefault(emptyMap())

    private suspend fun artistMix(
        p: TasteProfile, artistKey: String, ctx: Mixing,
        exclude: Set<String> = emptySet(), size: Int = MIX_SIZE,
    ): Pair<Pair<String, String>, List<Track>>? = coroutineScope {
        val circle = listOf(artistKey) + p.neighbors[artistKey].orEmpty().entries
            .sortedByDescending { it.value }.take(4).map { it.key }
        val ids = circle.mapNotNull { a -> p.known.values.firstOrNull { it.artist.lowercase() == a }?.artistId }
        val tracks = ids.map { id -> async { guarded("SmartMixes") { artistSongs(id, 4) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
        val display = p.known.values.firstOrNull { it.artist.lowercase() == artistKey }?.artist ?: artistKey
        pick(p, (tracks + p.known.values.filter { it.artist.lowercase() in circle }).distinctBy { it.id }, ctx, exclude, size)
            ?.let { ("a:$artistKey" to display) to it }
    }

    /**
     * ~80% best predicted, ~20% exploration; no disliked songs; spread artists out.
     * Score = your taste + world popularity + era balance. Popularity counts most for
     * songs you haven't played yet; once you have, your own behavior takes over.
     */
    private suspend fun pick(
        p: TasteProfile, candidates: List<Track>, base: Mixing,
        exclude: Set<String> = emptySet(), size: Int = MIX_SIZE, minimum: Int = 10,
    ): List<Track>? {
        val usable = candidates.filter {
            it.id !in exclude && it.id !in p.disliked && (p.songScore[it.id] ?: 0.0) > -1.5
        }
        if (usable.size < minimum) return null
        val ranks = withContext(Dispatchers.IO) { log.ranks(usable.map { it.id }) }
        Popularity.request(usable, ranks.keys)
        val ctx = Mixing(ranks, base.era)
        val scored = usable.map { it to p.predict(it) + boost(p, it, ctx) + Random.nextDouble(0.0, 0.15) }
            .sortedByDescending { it.second }
        val exploitCount = (size * 0.8).toInt()
        val exploit = scored.take(exploitCount).map { it.first }
        val explore = scored.drop(exploitCount).filter { it.first.id !in p.known && it.second > -0.2 }
            .shuffled().take(size - exploit.size).map { it.first }
        return spread((exploit + explore).shuffled())
    }

    private fun boost(p: TasteProfile, t: Track, ctx: Mixing): Double {
        val popularityWeight = if (t.id in p.songScore) 0.1 else 0.4
        val decade = t.year?.takeIf { it > 1900 }?.let { it / 10 * 10 }
        // Rotate songs out for a few days after you play them, so mixes stay fresh.
        val sinceMs = p.lastPlayed[t.id]?.let { System.currentTimeMillis() - it }
        val freshness = when {
            sinceMs == null -> 0.0
            sinceMs < DAY -> -0.9
            sinceMs < 4 * DAY -> -0.45
            else -> 0.0
        }
        return popularityWeight * Popularity.score(ctx.ranks[t.id]) + (decade?.let { ctx.era[it] } ?: 0.0) + freshness
    }

    /**
     * Corrects for what your library is full of. If 60s songs are 40% of your files
     * but only 15% of what you finish, 60s picks are nudged down (and vice versa).
     * Needs 40 finished plays with a known year before it kicks in. Range -0.5..+0.5.
     */
    private suspend fun eraBalance(p: TasteProfile): Map<Int, Double> {
        val played = HashMap<Int, Double>()
        p.lifetimeLikes.forEach { (id, plays) ->
            val year = p.known[id]?.year?.takeIf { it > 1900 } ?: return@forEach
            played.merge(year / 10 * 10, plays.toDouble()) { x, y -> x + y }
        }
        val playTotal = played.values.sum()
        if (playTotal < 40) return emptyMap()
        val library = decadeShares()
        if (library.isEmpty()) return emptyMap()
        return library.keys.associateWith { d ->
            val playShare = (played[d] ?: 0.0) / playTotal
            val libShare = library[d] ?: 0.0
            (ln((playShare + 0.02) / (libShare + 0.02)) / 2).coerceIn(-0.5, 0.5)
        }
    }

    private suspend fun decadeShares(): Map<Int, Double> {
        val counts = decadeCounts()
        val total = counts.values.sum().toDouble().takeIf { it > 0 } ?: return emptyMap()
        return counts.mapValues { it.value / total }
    }

    /** Songs in your library per decade (from album years), refreshed daily. */
    suspend fun decadeCounts(): Map<Int, Int> {
        libraryDecades?.let { (at, map) -> if (System.currentTimeMillis() - at < DAY) return map }
        val counts = HashMap<Int, Int>()
        var offset = 0
        while (offset < 3000) {
            val page = source.albums(AlbumOrder.Newest, 500, offset)
            page.forEach { a ->
                val y = a.year?.takeIf { it > 1900 } ?: return@forEach
                counts.merge(y / 10 * 10, a.songCount) { x, z -> x + z }
            }
            if (page.size < 500) break
            offset += 500
        }
        if (counts.isNotEmpty()) libraryDecades = System.currentTimeMillis() to counts
        return counts
    }

    /** Reorders so the same artist rarely plays twice in a row. */
    private fun spread(tracks: List<Track>): List<Track> {
        val pool = tracks.toMutableList()
        val out = ArrayList<Track>(tracks.size)
        while (pool.isNotEmpty()) {
            val lastArtist = out.lastOrNull()?.artist
            val i = pool.indexOfFirst { it.artist != lastArtist }.takeIf { it >= 0 } ?: 0
            out += pool.removeAt(i)
        }
        return out
    }

    // ---- Other mixes ----

    private fun onRepeat(p: TasteProfile): GeneratedMix? {
        val tracks = p.recentPlays.entries
            .filter { (p.songScore[it.key] ?: 0.0) > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { p.songScore[it.key] })
            .take(30).mapNotNull { p.known[it.key] }
        if (tracks.size < 10) return null
        return GeneratedMix(Card("smart:repeat", "On Repeat", "Your most-played songs right now", null, Kind.Mix), tracks)
    }

    private fun rediscover(p: TasteProfile, now: Long): GeneratedMix? {
        val tracks = p.lifetimeLikes.entries
            .filter { it.value >= 3 && now - (p.lastPlayed[it.key] ?: now) > 60 * DAY && it.key !in p.disliked }
            .sortedByDescending { it.value }.take(40).mapNotNull { p.known[it.key] }
        if (tracks.size < 10) return null
        return GeneratedMix(Card("smart:rediscover", "Rediscover", "Old favorites you haven't played lately", null, Kind.Mix), tracks.shuffled())
    }

    /** Songs you own but have never played, from artists you already like. */
    private suspend fun deepCuts(p: TasteProfile): GeneratedMix? = coroutineScope {
        val artistIds = p.topArtists.take(8)
            .mapNotNull { a -> p.known.values.firstOrNull { it.artist.lowercase() == a }?.artistId }
        if (artistIds.size < 3) return@coroutineScope null
        val tracks = artistIds.map { id -> async { guarded("SmartMixes") { artistSongs(id, 6) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
            .filter { it.id !in p.known }
            .distinctBy { it.id }
        if (tracks.size < 10) return@coroutineScope null
        // Best-known unplayed songs first, so Deep Cuts leans toward songs worth hearing.
        val ranks = withContext(Dispatchers.IO) { log.ranks(tracks.map { it.id }) }
        Popularity.request(tracks, ranks.keys)
        val ordered = tracks.sortedByDescending { Popularity.score(ranks[it.id]) + Random.nextDouble(0.0, 0.4) }
        GeneratedMix(
            Card("smart:deepcuts", "Deep Cuts", "Songs you own but never play, from artists you love", null, Kind.Mix),
            spread(ordered.take(MIX_SIZE).shuffled()),
        )
    }

    private suspend fun artistSongs(artistId: String, maxAlbums: Int): List<Track> = coroutineScope {
        source.artist(artistId).albums.take(maxAlbums)
            .map { a -> async { guarded("SmartMixes") { source.album(a.id).tracks }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
    }
}
