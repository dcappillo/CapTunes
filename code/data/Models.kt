package app.captunes.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

enum class Kind { Album, Playlist, Mix, Genre, Liked, Artist, Downloaded }

@Immutable
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val coverArt: String?,
    val durationMs: Long,
    val explicit: Boolean,
    val starred: Boolean,
    val genre: String? = null,
    val year: Int? = null,
    /** File type on the server ("mp3", "flac", "wma"…), so we know what needs converting. */
    val suffix: String? = null,
)

/** Anything shown as a tile: album, playlist, mix, genre, artist. */
@Immutable
data class Card(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArt: String?,
    val kind: Kind,
    val seed: Int = id.hashCode(),
)

@Immutable
data class CollectionDetail(
    val kind: Kind,
    val id: String,
    val title: String,
    val typeLabel: String,
    val owner: String,
    val ownerId: String?,
    val coverArt: String?,
    val year: Int?,
    val tracks: List<Track>,
    val starred: Boolean,
)

@Immutable
data class ArtistDetail(val id: String, val name: String, val coverArt: String?, val albums: List<Card>)

@Immutable
data class HomeData(
    val quickPicks: List<Card>,
    val mixes: List<Card>,
    val forYou: List<Card>,
    val decades: List<Card> = emptyList(),
    val recent: List<Card>,
    val frequent: List<Card>,
    val playlists: List<Card>,
    val newest: List<Card>,
    val random: List<Card>,
)

@Immutable
data class LibraryData(
    val playlists: List<Card> = emptyList(),
    val artists: List<Card> = emptyList(),
    val albums: List<Card> = emptyList(),
    val loading: Boolean = true,
)

@Immutable
data class SearchData(
    val songs: List<Track> = emptyList(),
    val artists: List<Card> = emptyList(),
    val albums: List<Card> = emptyList(),
    val playlists: List<Card> = emptyList(),
    val loading: Boolean = false,
)

@Immutable
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>, val source: String = "your server")

@Immutable
data class LyricLine(val startMs: Long?, val text: String)

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

fun SongDto.toTrack() = Track(
    id = id,
    title = title.ifBlank { "Untitled" },
    artist = artist?.ifBlank { null } ?: "Unknown artist",
    artistId = artistId,
    album = album.orEmpty(),
    albumId = albumId,
    coverArt = coverArt,
    durationMs = duration * 1000L,
    explicit = explicitStatus?.startsWith("e", ignoreCase = true) == true,
    starred = starred != null,
    genre = genre,
    year = year,
    suffix = suffix?.lowercase(),
)

fun AlbumDto.toCard() = Card(
    id = id,
    title = name,
    subtitle = listOfNotNull(artist, year?.toString()).joinToString(" · "),
    coverArt = coverArt,
    kind = Kind.Album,
)

fun PlaylistDto.toCard() = Card(
    id = id,
    title = name,
    subtitle = "Playlist · ${owner ?: "you"}",
    coverArt = coverArt,
    kind = Kind.Playlist,
)

fun ArtistDto.toCard() = Card(
    id = id,
    title = name,
    subtitle = "Artist",
    coverArt = coverArt,
    kind = Kind.Artist,
)
