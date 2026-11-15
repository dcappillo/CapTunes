package app.captunes.data

import app.captunes.util.guarded
import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Music stored on this phone or tablet. Android already keeps a catalog of every
 * song (title, artist, album, year, genre, length, cover), so we read that instead
 * of scanning files ourselves. Likes and playlists are kept in a small database here.
 */
class LocalSource(context: Context, private val playLog: PlayLog) : MusicSource {

    private val app = context.applicationContext
    private val store = LocalStore(app)
    private val mutex = Mutex()

    @Volatile private var index: LocalIndex? = null

    init {
        // New or deleted songs: rebuild the catalog next time it's needed.
        guarded("LocalSource") {
            app.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { index = null }
                },
            )
        }
    }

    override fun invalidate() { index = null }

    /** Builds (or returns) the catalog. Also handy for showing "Found 1,284 songs". */
    suspend fun library(): LocalIndex =
        index ?: mutex.withLock { index ?: withContext(Dispatchers.IO) { scan() }.also { index = it } }

    // ---- MusicSource ----

    override suspend fun ping() {
        if (!hasPermission(app)) throw IllegalStateException("Cap Tunes doesn't have permission to read your music")
        library()
    }

    override suspend fun albums(order: AlbumOrder, size: Int, offset: Int): List<AlbumInfo> {
        val lib = library()
        val ordered: List<LocalAlbum> = when (order) {
            AlbumOrder.Newest -> lib.albums.sortedByDescending { it.addedAt }
            AlbumOrder.Random -> lib.albums.shuffled()
            AlbumOrder.Recent, AlbumOrder.Frequent -> {
                val rows = withContext(Dispatchers.IO) { playLog.rows() }
                    .filter { it.track.albumId?.startsWith(ALBUM) == true }
                val ids = if (order == AlbumOrder.Recent) {
                    rows.sortedByDescending { it.startedAt }.mapNotNull { it.track.albumId }.distinct()
                } else {
                    rows.groupingBy { it.track.albumId }.eachCount().entries
                        .sortedByDescending { it.value }.mapNotNull { it.key }
                }
                ids.mapNotNull { lib.albumById[it] }
            }
        }
        return ordered.drop(offset).take(size).map { it.info(withTracks = false) }
    }

    override suspend fun album(id: String): AlbumInfo {
        val a = library().albumById[id] ?: throw IllegalArgumentException("Album not found")
        val liked = withContext(Dispatchers.IO) { store.isLiked(id) }
        return a.info(withTracks = true).copy(starred = liked)
    }

    override suspend fun artists(): List<ArtistInfo> =
        library().artists.sortedBy { it.name.lowercase() }.map { ArtistInfo(it.id, it.name, it.coverArt) }

    override suspend fun artist(id: String): ArtistInfo {
        val lib = library()
        val artist = lib.artists.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Artist not found")
        val albums = lib.albums.filter { a -> a.songs.any { it.track.artistId == id } }
        return ArtistInfo(artist.id, artist.name, artist.coverArt, albums.map { it.info(withTracks = false) })
    }

    override suspend fun playlists(): List<PlaylistInfo> {
        val lib = library()
        return withContext(Dispatchers.IO) { store.playlists() }.map { p ->
            val first = p.songIds.firstNotNullOfOrNull { lib.songById[it] }
            PlaylistInfo(p.id, p.name, null, first?.track?.coverArt, "%015d".format(p.changed))
        }
    }

    override suspend fun playlist(id: String): PlaylistInfo {
        val lib = library()
        val p = withContext(Dispatchers.IO) { store.playlists() }.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Playlist not found")
        val tracks = p.songIds.mapNotNull { lib.songById[it]?.track }
        return PlaylistInfo(p.id, p.name, null, tracks.firstOrNull()?.coverArt, "%015d".format(p.changed), tracks)
    }

    override suspend fun genres(): List<GenreInfo> =
        library().songs.mapNotNull { it.track.genre?.takeIf { g -> g.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .map { (name, count) -> GenreInfo(name, count) }

    override suspend fun songsByGenre(genre: String): List<Track> =
        library().songs.filter { it.track.genre.equals(genre, ignoreCase = true) }.map { it.track }

    override suspend fun randomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Track> =
        library().songs.asSequence()
            .filter { genre == null || it.track.genre.equals(genre, ignoreCase = true) }
            .filter { fromYear == null || (it.track.year ?: 0) >= fromYear }
            .filter { toYear == null || (it.track.year ?: Int.MAX_VALUE) <= toYear }
            .map { it.track }.toList().shuffled().take(size)

    override suspend fun search(query: String): SearchHits {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchHits()
        val lib = library()
        fun rank(text: String): Int = text.lowercase().let { if (it == q) 0 else if (it.startsWith(q)) 1 else 2 }
        val songs = lib.songs.filter {
            it.track.title.contains(q, true) || it.track.artist.contains(q, true) || it.track.album.contains(q, true)
        }.sortedBy { rank(it.track.title) }.take(25).map { it.track }
        val artists = lib.artists.filter { it.name.contains(q, true) }.sortedBy { rank(it.name) }.take(6)
            .map { ArtistInfo(it.id, it.name, it.coverArt) }
        val albums = lib.albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
            .sortedBy { rank(it.title) }.take(8).map { it.info(withTracks = false) }
        return SearchHits(songs, artists, albums)
    }

    override suspend fun starredSongs(): List<Track> {
        val lib = library()
        return withContext(Dispatchers.IO) { store.likedIds() }.mapNotNull { lib.songById[it]?.track?.copy(starred = true) }
    }

    override suspend fun setStarred(id: String, isAlbum: Boolean, starred: Boolean) =
        withContext(Dispatchers.IO) { store.setLiked(id, starred) }

    override suspend fun createPlaylist(name: String, songIds: List<String>) =
        withContext(Dispatchers.IO) { store.createPlaylist(name, songIds) }

    override suspend fun addToPlaylist(playlistId: String, songId: String) =
        withContext(Dispatchers.IO) { store.addToPlaylist(playlistId, songId) }

    override suspend fun renamePlaylist(playlistId: String, name: String) =
        withContext(Dispatchers.IO) { store.renamePlaylist(playlistId, name) }

    override suspend fun removeFromPlaylist(playlistId: String, index: Int) {
        val lib = library()
        withContext(Dispatchers.IO) {
            val saved = store.playlists().firstOrNull { it.id == playlistId }
                ?: throw IllegalArgumentException("Playlist not found")
            // The screen hides songs that are no longer on the phone, so [index] counts only
            // the visible ones. Translate it to the song's real position before deleting.
            val visible = saved.songIds.indices.filter { lib.songById.containsKey(saved.songIds[it]) }
            val position = visible.getOrNull(index) ?: throw IllegalArgumentException("Song not found")
            store.removeFromPlaylist(playlistId, position)
        }
    }

    override suspend fun deletePlaylist(playlistId: String) =
        withContext(Dispatchers.IO) { store.deletePlaylist(playlistId) }

    /** Nothing to report to; your play history is already kept on the phone. */
    override suspend fun scrobble(songId: String, submission: Boolean, timeMs: Long) = Unit

    /** Lyrics come from LRCLIB for local songs. */
    override suspend fun lyrics(songId: String): List<Lyrics> = emptyList()

    override fun streamUri(songId: String, suffix: String?, forceConvert: Boolean): String {
        val mediaId = songId.removePrefix(SONG).toLongOrNull() ?: return songId
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()
    }

    override fun coverUri(coverArt: String?, sizePx: Int): String? {
        val albumId = coverArt?.takeIf { it.startsWith(ALBUM) }?.removePrefix(ALBUM)?.toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId).toString()
    }

    // ---- Reading Android's music catalog ----

    private fun scan(): LocalIndex {
        val newApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
        )
        if (newApi) {
            columns += MediaStore.Audio.Media.GENRE
            columns += MediaStore.Audio.Media.ALBUM_ARTIST
        }
        // Real music only: skips ringtones, notifications, voice notes and clips under 30 seconds.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val songs = ArrayList<LocalSong>()
        app.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray(), selection, null, null,
        )?.use { c ->
            fun col(name: String) = c.getColumnIndex(name)
            val iId = col(MediaStore.Audio.Media._ID)
            val iTitle = col(MediaStore.Audio.Media.TITLE)
            val iArtist = col(MediaStore.Audio.Media.ARTIST)
            val iArtistId = col(MediaStore.Audio.Media.ARTIST_ID)
            val iAlbum = col(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = col(MediaStore.Audio.Media.ALBUM_ID)
            val iDuration = col(MediaStore.Audio.Media.DURATION)
            val iTrack = col(MediaStore.Audio.Media.TRACK)
            val iYear = col(MediaStore.Audio.Media.YEAR)
            val iName = col(MediaStore.Audio.Media.DISPLAY_NAME)
            val iAdded = col(MediaStore.Audio.Media.DATE_ADDED)
            val iGenre = if (newApi) col(MediaStore.Audio.Media.GENRE) else -1
            val iAlbumArtist = if (newApi) col(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
            while (c.moveToNext()) {
                val name = c.getString(iName).orEmpty()
                val suffix = name.substringAfterLast('.', "").lowercase().ifBlank { null }
                // Android can't play these, so they're left out rather than failing later.
                if (Session.needsConversion(suffix)) continue
                val id = c.getLong(iId)
                val artist = clean(c.getString(iArtist)) ?: "Unknown artist"
                val albumId = c.getLong(iAlbumId)
                val trackNo = if (c.isNull(iTrack)) 0 else c.getInt(iTrack)
                val year = if (c.isNull(iYear)) null else c.getInt(iYear).takeIf { it > 0 }
                val track = Track(
                    id = "$SONG$id",
                    title = clean(c.getString(iTitle)) ?: name.substringBeforeLast('.'),
                    artist = artist,
                    artistId = "$ARTIST${c.getLong(iArtistId)}",
                    album = clean(c.getString(iAlbum)) ?: "Unknown album",
                    albumId = "$ALBUM$albumId",
                    coverArt = "$ALBUM$albumId",
                    durationMs = c.getLong(iDuration),
                    explicit = false,
                    starred = false,
                    genre = if (iGenre >= 0) clean(c.getString(iGenre)) else null,
                    year = year,
                    suffix = suffix,
                )
                songs += LocalSong(
                    track = track,
                    disc = trackNo / 1000,
                    trackNo = trackNo % 1000,
                    addedAt = c.getLong(iAdded),
                    albumArtist = if (iAlbumArtist >= 0) clean(c.getString(iAlbumArtist)) else null,
                )
            }
        }
        return LocalIndex.build(songs)
    }

    companion object {
        const val SONG = "local:"
        const val ALBUM = "lalbum:"
        const val ARTIST = "lartist:"

        /** The permission Android asks for: "music and audio" on 13+, storage before that. */
        val permission: String
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
    }
}

class LocalSong(
    val track: Track,
    val disc: Int,
    val trackNo: Int,
    val addedAt: Long,
    val albumArtist: String?,
)

class LocalAlbum(val id: String, val title: String, val artist: String, val songs: List<LocalSong>) {
    val year: Int? = songs.mapNotNull { it.track.year }.maxOrNull()
    val addedAt: Long = songs.maxOf { it.addedAt }
    val artistId: String? = songs.groupingBy { it.track.artistId }.eachCount().maxByOrNull { it.value }?.key

    fun info(withTracks: Boolean) = AlbumInfo(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        coverArt = id,
        year = year,
        songCount = songs.size,
        starred = false,
        tracks = if (withTracks) songs.sortedWith(compareBy({ it.disc }, { it.trackNo }, { it.track.title })).map { it.track }
        else emptyList(),
    )
}

class LocalArtist(val id: String, val name: String, val coverArt: String?, val songCount: Int)

/** The whole on-phone catalog, grouped the ways the app needs it. */
class LocalIndex(
    val songs: List<LocalSong>,
    val albums: List<LocalAlbum>,
    val artists: List<LocalArtist>,
) {
    val songById: Map<String, LocalSong> = songs.associateBy { it.track.id }
    val albumById: Map<String, LocalAlbum> = albums.associateBy { it.id }

    companion object {
        fun build(songs: List<LocalSong>): LocalIndex {
            val albums = songs.groupBy { it.track.albumId ?: "" }.map { (id, list) ->
                val artist = list.mapNotNull { it.albumArtist }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                    ?: list.map { it.track.artist }.distinct().let { if (it.size == 1) it[0] else "Various artists" }
                LocalAlbum(id, list.first().track.album, artist, list)
            }
            val artists = songs.groupBy { it.track.artistId ?: "" }.map { (id, list) ->
                LocalArtist(id, list.first().track.artist, list.first().track.coverArt, list.size)
            }
            return LocalIndex(songs, albums, artists)
        }
    }
}

/** Likes and playlists for the on-phone library. */
private class LocalStore(context: Context) : SQLiteOpenHelper(context, "local_library.db", null, 1) {

    class Playlist(val id: String, val name: String, val changed: Long, val songIds: List<String>)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE liked (id TEXT PRIMARY KEY, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, changed INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_songs (playlist_id INTEGER NOT NULL, pos INTEGER NOT NULL, song_id TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun likedIds(): List<String> = readableDatabase.rawQuery("SELECT id FROM liked ORDER BY at DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun isLiked(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM liked WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    fun setLiked(id: String, liked: Boolean) {
        if (liked) {
            writableDatabase.insertWithOnConflict(
                "liked", null,
                ContentValues().apply { put("id", id); put("at", System.currentTimeMillis()) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } else {
            writableDatabase.delete("liked", "id = ?", arrayOf(id))
        }
    }

    fun playlists(): List<Playlist> {
        val songs = HashMap<Long, MutableList<String>>()
        readableDatabase.rawQuery("SELECT playlist_id, song_id FROM playlist_songs ORDER BY playlist_id, pos", null).use { c ->
            while (c.moveToNext()) songs.getOrPut(c.getLong(0)) { mutableListOf() } += c.getString(1)
        }
        return readableDatabase.rawQuery("SELECT id, name, changed FROM playlists", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    add(Playlist("$PLAYLIST$id", c.getString(1), c.getLong(2), songs[id].orEmpty()))
                }
            }
        }
    }

    fun createPlaylist(name: String, songIds: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert("playlists", null, ContentValues().apply {
                put("name", name)
                put("changed", System.currentTimeMillis())
            })
            songIds.forEachIndexed { i, s -> addRow(db, id, i, s) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addToPlaylist(playlistId: String, songId: String) {
        val id = playlistId.removePrefix(PLAYLIST).toLongOrNull() ?: return
        val db = writableDatabase
        val next = db.rawQuery("SELECT COALESCE(MAX(pos), -1) + 1 FROM playlist_songs WHERE playlist_id = ?", arrayOf("$id"))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        addRow(db, id, next, songId)
        db.update("playlists", ContentValues().apply { put("changed", System.currentTimeMillis()) }, "id = ?", arrayOf("$id"))
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val id = playlistId.removePrefix(PLAYLIST).toLongOrNull() ?: return
        writableDatabase.update(
            "playlists",
            ContentValues().apply { put("name", name); put("changed", System.currentTimeMillis()) },
            "id = ?", arrayOf("$id"),
        )
    }

    fun removeFromPlaylist(playlistId: String, index: Int) {
        val id = playlistId.removePrefix(PLAYLIST).toLongOrNull() ?: return
        val db = writableDatabase
        // Find the row sitting at that position, then delete just that one.
        val row = db.rawQuery(
            "SELECT rowid FROM playlist_songs WHERE playlist_id = ? ORDER BY pos LIMIT 1 OFFSET ?",
            arrayOf("$id", "$index"),
        ).use { if (it.moveToFirst()) it.getLong(0) else null } ?: return
        db.delete("playlist_songs", "rowid = ?", arrayOf("$row"))
        db.update("playlists", ContentValues().apply { put("changed", System.currentTimeMillis()) }, "id = ?", arrayOf("$id"))
    }

    fun deletePlaylist(playlistId: String) {
        val id = playlistId.removePrefix(PLAYLIST).toLongOrNull() ?: return
        val db = writableDatabase
        db.delete("playlist_songs", "playlist_id = ?", arrayOf("$id"))
        db.delete("playlists", "id = ?", arrayOf("$id"))
    }

    private fun addRow(db: SQLiteDatabase, playlistId: Long, pos: Int, songId: String) {
        db.insert("playlist_songs", null, ContentValues().apply {
            put("playlist_id", playlistId)
            put("pos", pos)
            put("song_id", songId)
        })
    }

    companion object { const val PLAYLIST = "lpl:" }
}
