package app.captunes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** JSON shapes returned by Navidrome's Subsonic / OpenSubsonic API. */

@Serializable
data class Envelope(@SerialName("subsonic-response") val response: ApiResponse)

@Serializable
data class ApiResponse(
    val status: String = "",
    val error: ApiError? = null,
    val albumList2: AlbumListDto? = null,
    val album: AlbumDto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDto? = null,
    val artists: ArtistsDto? = null,
    val artist: ArtistDto? = null,
    val searchResult3: SearchDto? = null,
    val genres: GenresDto? = null,
    val songsByGenre: SongListDto? = null,
    val randomSongs: SongListDto? = null,
    val starred2: StarredDto? = null,
    val lyricsList: LyricsListDto? = null,
)

@Serializable
data class ApiError(val code: Int = 0, val message: String = "")

@Serializable
data class SongDto(
    val id: String,
    val title: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val starred: String? = null,
    val explicitStatus: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val suffix: String? = null,
)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val starred: String? = null,
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class AlbumListDto(val album: List<AlbumDto> = emptyList())

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String = "",
    val owner: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val changed: String? = null,
    val entry: List<SongDto> = emptyList(),
)

@Serializable
data class PlaylistsDto(val playlist: List<PlaylistDto> = emptyList())

@Serializable
data class ArtistDto(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class ArtistIndexDto(val name: String = "", val artist: List<ArtistDto> = emptyList())

@Serializable
data class ArtistsDto(val index: List<ArtistIndexDto> = emptyList())

@Serializable
data class SearchDto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class GenreDto(val value: String = "", val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class GenresDto(val genre: List<GenreDto> = emptyList())

@Serializable
data class SongListDto(val song: List<SongDto> = emptyList())

@Serializable
data class StarredDto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class LyricsListDto(val structuredLyrics: List<StructuredLyricsDto> = emptyList())

@Serializable
data class StructuredLyricsDto(val synced: Boolean = false, val line: List<LyricLineDto> = emptyList())

@Serializable
data class LyricLineDto(val start: Long? = null, val value: String = "")
