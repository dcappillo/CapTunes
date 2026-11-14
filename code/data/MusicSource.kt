package app.captunes.data

/**
 * Where the music comes from. Everything above this line (Repository, mixes,
 * screens, the player) talks only to a MusicSource, never to Navidrome directly,
 * so a second kind of library (songs stored on the phone) can slot in later.
 */
interface MusicSource {
    /** Checks the source is reachable (and the login works, for a server). */
    suspend fun ping()

    suspend fun albums(order: AlbumOrder, size: Int, offset: Int = 0): List<AlbumInfo>
    suspend fun album(id: String): AlbumInfo
    suspend fun artists(): List<ArtistInfo>
    suspend fun artist(id: String): ArtistInfo
    suspend fun playlists(): List<PlaylistInfo>
    suspend fun playlist(id: String): PlaylistInfo
    suspend fun genres(): List<GenreInfo>
    suspend fun songsByGenre(genre: String): List<Track>
    suspend fun randomSongs(size: Int, genre: String? = null, fromYear: Int? = null, toYear: Int? = null): List<Track>
    suspend fun search(query: String): SearchHits

    suspend fun starredSongs(): List<Track>
    suspend fun setStarred(id: String, isAlbum: Boolean, starred: Boolean)
    suspend fun createPlaylist(name: String, songIds: List<String>)
    suspend fun addToPlaylist(playlistId: String, songId: String)

    /** Tells the source a song started (submission=false) or was listened to. */
    suspend fun scrobble(songId: String, submission: Boolean, timeMs: Long)

    /** Lyrics the source itself has (embedded tags, .lrc files). Empty if none. */
    suspend fun lyrics(songId: String): List<Lyrics>

    /** Address the player opens for a song. [forceConvert] asks for a playable MP3 copy. */
    fun streamUri(songId: String, suffix: String?, forceConvert: Boolean = false): String

    /** Address of a cover image, or null if there isn't one. */
    fun coverUri(coverArt: String?, sizePx: Int = 600): String?

    /** Forget anything cached so the next read sees the latest library. */
    fun invalidate() {}
}

enum class AlbumOrder { Recent, Frequent, Newest, Random }

data class AlbumInfo(
    val id: String,
    val title: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val year: Int?,
    val songCount: Int,
    val starred: Boolean,
    val tracks: List<Track> = emptyList(),
) {
    fun toCard() = Card(
        id = id,
        title = title,
        subtitle = listOfNotNull(artist, year?.toString()).joinToString(" · "),
        coverArt = coverArt,
        kind = Kind.Album,
    )
}

data class ArtistInfo(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albums: List<AlbumInfo> = emptyList(),
) {
    fun toCard() = Card(id = id, title = name, subtitle = "Artist", coverArt = coverArt, kind = Kind.Artist)
}

data class PlaylistInfo(
    val id: String,
    val name: String,
    val owner: String?,
    val coverArt: String?,
    /** Last-changed time as sortable text (newest sorts last); null if unknown. */
    val changed: String?,
    val tracks: List<Track> = emptyList(),
) {
    fun toCard() = Card(
        id = id,
        title = name,
        subtitle = "Playlist · ${owner ?: "you"}",
        coverArt = coverArt,
        kind = Kind.Playlist,
    )
}

data class GenreInfo(val name: String, val songCount: Int)

data class SearchHits(
    val songs: List<Track> = emptyList(),
    val artists: List<ArtistInfo> = emptyList(),
    val albums: List<AlbumInfo> = emptyList(),
)
