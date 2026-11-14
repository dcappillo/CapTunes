package app.captunes.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Turns raw Navidrome responses into what the screens show. */
class Repository(private val source: MusicSource, private val playLog: PlayLog) {

    private val smart = SmartMixes(source, playLog)

    init { Popularity.start(playLog) }

    /** Current taste profile, built from the on-device play log. */
    fun taste(): TasteProfile = Taste.build(playLog.rows())

    fun playLog(): PlayLog = playLog

    suspend fun ping() = source.ping()

    suspend fun home(): HomeData = coroutineScope {
        val recent = async { runCatching { source.albums(AlbumOrder.Recent, 12) }.getOrDefault(emptyList()) }
        val frequent = async { runCatching { source.albums(AlbumOrder.Frequent, 12) }.getOrDefault(emptyList()) }
        val newest = async { source.albums(AlbumOrder.Newest, 12) }
        val random = async { source.albums(AlbumOrder.Random, 12) }
        val playlists = async { runCatching { source.playlists() }.getOrDefault(emptyList()) }
        val genres = async { runCatching { source.genres() }.getOrDefault(emptyList()) }
        val decades = async { runCatching { decadeCards() }.getOrDefault(emptyList()) }

        val playlistCards = playlists.await().map { it.toCard() }
        val recentCards = recent.await().map { it.toCard() }
        val newestCards = newest.await().map { it.toCard() }
        val quick = (playlistCards.take(3) + recentCards + newestCards)
            .distinctBy { it.id }.take(8)

        val profile = withContext(Dispatchers.IO) { runCatching { taste() }.getOrNull() }
        val generated = profile?.let { runCatching { smart.build(it) }.getOrNull() }.orEmpty()
        val smartDaily = generated.filter { it.card.id.startsWith("smart:daily") }.map { it.card }
        val learning = (profile?.totalPlays ?: 0).coerceAtMost(SmartMixes.MIN_PLAYS_FOR_DAILY)

        HomeData(
            quickPicks = quick,
            mixes = smartDaily.ifEmpty { mixCards(genres.await(), learning) },
            forYou = generated.filterNot { it.card.id.startsWith("smart:daily") }.map { it.card },
            decades = decades.await(),
            recent = recentCards,
            frequent = frequent.await().map { it.toCard() },
            playlists = playlistCards,
            newest = newestCards,
            random = random.await().map { it.toCard() },
        )
    }

    /** One card per decade with at least 15 songs, oldest first. */
    private suspend fun decadeCards(): List<Card> = smart.decadeCounts()
        .filter { it.value >= 15 }
        .toSortedMap()
        .map { (d, n) -> Card("decade:$d", SmartMixes.decadeName(d), "%,d songs".format(n), null, Kind.Mix) }

    /** More songs for the mix you're playing, so it never runs dry. */
    suspend fun moreForMix(id: String, exclude: Set<String>): List<Track> =
        if (id.startsWith("smart:") || id.startsWith("decade:")) {
            smart.more(id, withContext(Dispatchers.IO) { taste() }, exclude)
        } else {
            // Starter mixes (before your taste is learned) are random songs from a genre.
            source.randomSongs(40, id.ifBlank { null }).filter { it.id !in exclude }.take(25)
        }

    private fun mixCards(genres: List<GenreInfo>, learning: Int): List<Card> {
        val note = if (learning < SmartMixes.MIN_PLAYS_FOR_DAILY) " · learning your taste ($learning/${SmartMixes.MIN_PLAYS_FOR_DAILY})" else ""
        val top = genres.filter { it.name.isNotBlank() }.sortedByDescending { it.songCount }.take(5)
        if (top.isEmpty()) {
            return listOf(Card("", "Daily Mix 1", "A bit of everything$note", null, Kind.Mix))
        }
        return top.mapIndexed { i, g ->
            Card(g.name, "Daily Mix ${i + 1}", "${g.name} and more$note", null, Kind.Mix)
        }
    }

    suspend fun genres(): List<Card> = source.genres()
        .filter { it.name.isNotBlank() }
        .sortedByDescending { it.songCount }
        .map { Card(it.name, it.name, "${it.songCount} songs", null, Kind.Genre) }

    suspend fun library(): LibraryData = coroutineScope {
        val playlists = async { source.playlists() }
        val artists = async { source.artists() }
        val albums = async { allAlbums() }
        LibraryData(
            playlists = playlists.await().sortedByDescending { it.changed }.map { it.toCard() },
            artists = artists.await().map { it.toCard() },
            albums = albums.await().map { it.toCard() },
            loading = false,
        )
    }

    /** Pages through the whole album list, newest first (capped at 3,000). */
    private suspend fun allAlbums(): List<AlbumInfo> {
        val all = mutableListOf<AlbumInfo>()
        while (all.size < 3000) {
            val page = source.albums(AlbumOrder.Newest, 500, all.size)
            all += page
            if (page.size < 500) break
        }
        return all
    }

    suspend fun collection(kind: Kind, id: String): CollectionDetail = when (kind) {
        Kind.Album -> source.album(id).let { a ->
            CollectionDetail(kind, id, a.title, "Album", a.artist ?: "Unknown artist", a.artistId,
                a.coverArt, a.year, a.tracks, a.starred)
        }
        Kind.Playlist -> source.playlist(id).let { p ->
            CollectionDetail(kind, id, p.name, "Playlist", p.owner ?: "You", null,
                p.coverArt, null, p.tracks, false)
        }
        Kind.Mix -> smartMix(id) ?: CollectionDetail(kind, id, mixTitle(id), "Mix", "Cap Tunes", null, null, null,
            source.randomSongs(50, id.ifBlank { null }), false)
        Kind.Genre -> CollectionDetail(kind, id, id, "Genre", "Your library", null, null, null,
            source.songsByGenre(id), false)
        Kind.Liked -> CollectionDetail(kind, id, "Liked Songs", "Playlist", "You", null, null, null,
            likedSongs(), false)
        Kind.Downloaded -> CollectionDetail(kind, id, "Downloaded", "Playlist", "On this phone", null, null, null,
            app.captunes.playback.Downloads.downloadedTracks(), false)
        Kind.Artist -> throw IllegalArgumentException("Use artist() for artists")
    }

    private suspend fun smartMix(id: String): CollectionDetail? {
        if (id.startsWith("decade:")) {
            val d = id.removePrefix("decade:").toIntOrNull() ?: return null
            // Rebuilt each time you open it, so it's different every visit.
            val mix = smart.decade(withContext(Dispatchers.IO) { taste() }, d) ?: return null
            return CollectionDetail(Kind.Mix, id, mix.card.title, "Mix", "Made for you", null, null, null, mix.tracks, false)
        }
        if (!id.startsWith("smart:")) return null
        val mix = MixStore.get(id) ?: run {
            runCatching { smart.build(withContext(Dispatchers.IO) { taste() }) }
            MixStore.get(id)
        } ?: return null
        return CollectionDetail(Kind.Mix, id, mix.card.title, "Mix", "Made for you", null, null, null, mix.tracks, false)
    }

    /** Records "Not for me" so the mixes stop picking this song. */
    fun dislike(track: Track) = playLog.dislike(track)

    private fun mixTitle(genre: String) = if (genre.isBlank()) "Daily Mix" else "$genre Mix"

    suspend fun artist(id: String): ArtistDetail = source.artist(id).let { a ->
        ArtistDetail(a.id, a.name, a.coverArt, a.albums.sortedByDescending { it.year ?: 0 }.map { it.toCard() })
    }

    /** Every song on an artist's albums, for "Play artist". */
    suspend fun artistTracks(albums: List<Card>): List<Track> = coroutineScope {
        albums.take(15).map { c -> async { runCatching { source.album(c.id).tracks }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
    }

    suspend fun likedSongs(): List<Track> = source.starredSongs()

    suspend fun search(query: String, playlists: List<Card>): SearchData {
        val r = source.search(query)
        return SearchData(
            songs = r.songs,
            artists = r.artists.map { it.toCard() },
            albums = r.albums.map { it.toCard() },
            playlists = playlists.filter { it.title.contains(query, ignoreCase = true) },
        )
    }

    suspend fun setStarred(id: String, isAlbum: Boolean, starred: Boolean) {
        source.setStarred(id, isAlbum, starred)
    }

    private val lyricsCache = java.util.concurrent.ConcurrentHashMap<String, Lyrics>()
    private val noLyrics = Lyrics(false, emptyList())

    /**
     * Synced lyrics if anyone has them: your server first (embedded tags or .lrc),
     * then LRCLIB, then plain lyrics from either. Cached per song for this session.
     */
    suspend fun lyrics(track: Track): Lyrics? {
        lyricsCache[track.id]?.let { return it.takeIf { l -> l.lines.isNotEmpty() } }
        val list = runCatching { source.lyrics(track.id) }.getOrDefault(emptyList())
        val serverSynced = list.firstOrNull { it.synced && it.lines.isNotEmpty() }
        val serverPlain = list.firstOrNull { it.lines.isNotEmpty() }
        val result = when {
            serverSynced != null -> serverSynced
            else -> {
                val online = runCatching { LrcLib.find(track) }.getOrNull()
                when {
                    online?.synced == true -> online
                    serverPlain != null -> Lyrics(false, serverPlain.lines.map { LyricLine(null, it.text) })
                    else -> online
                }
            }
        }
        lyricsCache[track.id] = result ?: noLyrics
        return result
    }

    suspend fun createPlaylist(name: String, songIds: List<String>) = source.createPlaylist(name, songIds)

    suspend fun addToPlaylist(playlistId: String, songId: String) = source.addToPlaylist(playlistId, songId)

    suspend fun scrobble(songId: String, submission: Boolean, timeMs: Long) =
        source.scrobble(songId, submission, timeMs)
}
