package app.captunes.data

/** Your Navidrome server (or any Subsonic-compatible server). */
class SubsonicSource(private val api: SubsonicApi, private val session: Session) : MusicSource {

    override suspend fun ping() = api.ping()

    override suspend fun albums(order: AlbumOrder, size: Int, offset: Int): List<AlbumInfo> {
        val type = when (order) {
            AlbumOrder.Recent -> "recent"
            AlbumOrder.Frequent -> "frequent"
            AlbumOrder.Newest -> "newest"
            AlbumOrder.Random -> "random"
        }
        return api.albumList(type, size, offset).map { it.toInfo() }
    }

    override suspend fun album(id: String): AlbumInfo = api.album(id).toInfo()

    override suspend fun artists(): List<ArtistInfo> = api.artists().map { it.toInfo() }

    override suspend fun artist(id: String): ArtistInfo = api.artist(id).toInfo()

    override suspend fun playlists(): List<PlaylistInfo> = api.playlists().map { it.toInfo() }

    override suspend fun playlist(id: String): PlaylistInfo = api.playlist(id).toInfo()

    override suspend fun genres(): List<GenreInfo> = api.genres().map { GenreInfo(it.value, it.songCount) }

    override suspend fun songsByGenre(genre: String): List<Track> = api.songsByGenre(genre).map { it.toTrack() }

    override suspend fun randomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Track> =
        api.randomSongs(size, genre, fromYear, toYear).map { it.toTrack() }

    override suspend fun search(query: String): SearchHits = api.search(query).let { r ->
        SearchHits(
            songs = r.song.map { it.toTrack() },
            artists = r.artist.map { it.toInfo() },
            albums = r.album.map { it.toInfo() },
        )
    }

    override suspend fun starredSongs(): List<Track> = api.starred().song.map { it.toTrack() }

    override suspend fun setStarred(id: String, isAlbum: Boolean, starred: Boolean) {
        if (starred) api.star(id, isAlbum) else api.unstar(id, isAlbum)
    }

    override suspend fun createPlaylist(name: String, songIds: List<String>) = api.createPlaylist(name, songIds)

    override suspend fun addToPlaylist(playlistId: String, songId: String) = api.addToPlaylist(playlistId, songId)

    override suspend fun renamePlaylist(playlistId: String, name: String) = api.renamePlaylist(playlistId, name)

    override suspend fun removeFromPlaylist(playlistId: String, index: Int) = api.removeFromPlaylist(playlistId, index)

    override suspend fun deletePlaylist(playlistId: String) = api.deletePlaylist(playlistId)

    override suspend fun scrobble(songId: String, submission: Boolean, timeMs: Long) =
        api.scrobble(songId, submission, timeMs)

    override suspend fun lyrics(songId: String): List<Lyrics> =
        api.lyrics(songId)?.structuredLyrics.orEmpty().map { l ->
            Lyrics(l.synced, l.line.map { LyricLine(if (l.synced) it.start else null, it.value) })
        }

    override fun streamUri(songId: String, suffix: String?, forceConvert: Boolean): String =
        session.streamUrl(songId, suffix, forceConvert || Session.needsConversion(suffix))

    override fun coverUri(coverArt: String?, sizePx: Int): String? = session.coverUrl(coverArt, sizePx)
}

private fun AlbumDto.toInfo() = AlbumInfo(
    id = id,
    title = name,
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    year = year,
    songCount = songCount,
    starred = starred != null,
    tracks = song.map { it.toTrack() },
)

private fun ArtistDto.toInfo() = ArtistInfo(id, name, coverArt, album.map { it.toInfo() })

private fun PlaylistDto.toInfo() = PlaylistInfo(id, name, owner, coverArt, changed, entry.map { it.toTrack() })
