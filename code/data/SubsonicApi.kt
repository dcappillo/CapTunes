package app.captunes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

object Net {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
}

/** Thin wrapper over the Subsonic REST endpoints Cap Tunes uses. */
class SubsonicApi(private val session: Session) {

    private suspend fun call(
        endpoint: String,
        params: List<Pair<String, String>> = emptyList(),
    ): ApiResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(session.url(endpoint, params)).build()
        Net.http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Server answered HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw ApiException("Empty response")
            val result = Net.json.decodeFromString(Envelope.serializer(), body).response
            if (result.status != "ok") {
                throw ApiException(result.error?.message?.ifBlank { null } ?: "Server error")
            }
            result
        }
    }

    suspend fun ping() { call("ping") }

    suspend fun albumList(type: String, size: Int, offset: Int = 0): List<AlbumDto> =
        call("getAlbumList2", listOf("type" to type, "size" to "$size", "offset" to "$offset"))
            .albumList2?.album.orEmpty()

    suspend fun album(id: String): AlbumDto =
        call("getAlbum", listOf("id" to id)).album ?: throw ApiException("Album not found")

    suspend fun playlists(): List<PlaylistDto> = call("getPlaylists").playlists?.playlist.orEmpty()

    suspend fun playlist(id: String): PlaylistDto =
        call("getPlaylist", listOf("id" to id)).playlist ?: throw ApiException("Playlist not found")

    suspend fun artists(): List<ArtistDto> =
        call("getArtists").artists?.index.orEmpty().flatMap { it.artist }

    suspend fun artist(id: String): ArtistDto =
        call("getArtist", listOf("id" to id)).artist ?: throw ApiException("Artist not found")

    suspend fun search(query: String): SearchDto =
        call(
            "search3",
            listOf("query" to query, "artistCount" to "6", "albumCount" to "8", "songCount" to "25"),
        ).searchResult3 ?: SearchDto()

    suspend fun genres(): List<GenreDto> = call("getGenres").genres?.genre.orEmpty()

    suspend fun songsByGenre(genre: String, count: Int = 200): List<SongDto> =
        call("getSongsByGenre", listOf("genre" to genre, "count" to "$count"))
            .songsByGenre?.song.orEmpty()

    suspend fun randomSongs(size: Int, genre: String? = null, fromYear: Int? = null, toYear: Int? = null): List<SongDto> {
        val params = mutableListOf("size" to "$size")
        if (!genre.isNullOrBlank()) params += "genre" to genre
        if (fromYear != null) params += "fromYear" to "$fromYear"
        if (toYear != null) params += "toYear" to "$toYear"
        return call("getRandomSongs", params).randomSongs?.song.orEmpty()
    }

    suspend fun starred(): StarredDto = call("getStarred2").starred2 ?: StarredDto()

    suspend fun star(id: String, isAlbum: Boolean) {
        call("star", listOf((if (isAlbum) "albumId" else "id") to id))
    }

    suspend fun unstar(id: String, isAlbum: Boolean) {
        call("unstar", listOf((if (isAlbum) "albumId" else "id") to id))
    }

    /** Logs a play in Navidrome. submission=false only marks "now playing". */
    suspend fun scrobble(songId: String, submission: Boolean, timeMs: Long) {
        call(
            "scrobble",
            listOf("id" to songId, "submission" to "$submission", "time" to "$timeMs"),
        )
    }

    suspend fun lyrics(songId: String): LyricsListDto? =
        call("getLyricsBySongId", listOf("id" to songId)).lyricsList

    suspend fun createPlaylist(name: String, songIds: List<String>) {
        call("createPlaylist", listOf("name" to name) + songIds.map { "songId" to it })
    }

    suspend fun addToPlaylist(playlistId: String, songId: String) {
        call("updatePlaylist", listOf("playlistId" to playlistId, "songIdToAdd" to songId))
    }
}
