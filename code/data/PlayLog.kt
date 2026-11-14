package app.captunes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** How a play ended. Drives both Wrapped and the taste engine. */
enum class Outcome(val code: Int) {
    Complete(0),   // played to (nearly) the end
    Partial(1),    // stopped or replaced, not a skip
    LateSkip(2),   // skipped after 30 s
    EarlySkip(3),  // skipped in the first 30 s
    Dislike(4);    // "Not for me"

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: Partial
    }
}

data class PlayRow(
    val track: Track,
    val startedAt: Long,
    val listenedMs: Long,
    val outcome: Outcome,
)

/**
 * On-device listening history (SQLite, no extra libraries).
 * Written by the playback service, read by Wrapped and the smart mixes.
 */
class PlayLog private constructor(context: Context) :
    SQLiteOpenHelper(context, "playlog.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE plays (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                song_id TEXT NOT NULL,
                title TEXT, artist TEXT, artist_id TEXT,
                album TEXT, album_id TEXT, genre TEXT, cover_art TEXT,
                duration_ms INTEGER, explicit INTEGER,
                started_at INTEGER NOT NULL,
                listened_ms INTEGER NOT NULL,
                outcome INTEGER NOT NULL,
                year INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX plays_time ON plays(started_at)")
        db.execSQL("CREATE INDEX plays_song ON plays(song_id)")
        createPopularity(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE plays ADD COLUMN year INTEGER")
            createPopularity(db)
        }
    }

    /** World popularity per song (Deezer rank 0..1,000,000), cached for 90 days. */
    private fun createPopularity(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS popularity (song_id TEXT PRIMARY KEY, rank INTEGER NOT NULL, fetched_at INTEGER NOT NULL)")
    }

    fun ranks(ids: Collection<String>, maxAgeMs: Long = 90L * 86_400_000): Map<String, Int> {
        val out = HashMap<String, Int>()
        val oldest = System.currentTimeMillis() - maxAgeMs
        ids.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT song_id, rank FROM popularity WHERE fetched_at >= ? AND song_id IN ($marks)",
                arrayOf(oldest.toString()) + chunk.toTypedArray(),
            ).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getInt(1) }
        }
        return out
    }

    fun saveRank(songId: String, rank: Int) {
        val values = ContentValues().apply {
            put("song_id", songId)
            put("rank", rank)
            put("fetched_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("popularity", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun record(track: Track, startedAt: Long, listenedMs: Long, outcome: Outcome) {
        val values = ContentValues().apply {
            put("song_id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("artist_id", track.artistId)
            put("album", track.album)
            put("album_id", track.albumId)
            put("genre", track.genre)
            put("cover_art", track.coverArt)
            put("duration_ms", track.durationMs)
            put("explicit", if (track.explicit) 1 else 0)
            put("started_at", startedAt)
            put("listened_ms", listenedMs)
            put("outcome", outcome.code)
            put("year", track.year)
        }
        writableDatabase.insert("plays", null, values)
    }

    fun dislike(track: Track) = record(track, System.currentTimeMillis(), 0, Outcome.Dislike)

    /** Rows with startedAt in [fromMs, toMs), oldest first. */
    fun rows(fromMs: Long = 0, toMs: Long = Long.MAX_VALUE): List<PlayRow> {
        val out = ArrayList<PlayRow>()
        readableDatabase.rawQuery(
            "SELECT song_id,title,artist,artist_id,album,album_id,genre,cover_art,duration_ms,explicit," +
                "started_at,listened_ms,outcome,year FROM plays WHERE started_at >= ? AND started_at < ? " +
                "ORDER BY started_at",
            arrayOf(fromMs.toString(), toMs.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val track = Track(
                    id = c.getString(0),
                    title = c.getString(1).orEmpty(),
                    artist = c.getString(2) ?: "Unknown artist",
                    artistId = c.getString(3),
                    album = c.getString(4).orEmpty(),
                    albumId = c.getString(5),
                    coverArt = c.getString(7),
                    durationMs = c.getLong(8),
                    explicit = c.getInt(9) == 1,
                    starred = false,
                    genre = c.getString(6),
                    year = if (c.isNull(13)) null else c.getInt(13),
                )
                out += PlayRow(track, c.getLong(10), c.getLong(11), Outcome.of(c.getInt(12)))
            }
        }
        return out
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM plays", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    companion object {
        @Volatile private var instance: PlayLog? = null

        fun get(context: Context): PlayLog = instance ?: synchronized(this) {
            instance ?: PlayLog(context.applicationContext).also { instance = it }
        }
    }
}
