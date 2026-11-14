package app.captunes.playback

import android.content.Context
import app.captunes.data.Net
import app.captunes.data.Track
import kotlinx.serialization.Serializable
import java.io.File

/** Everything needed to pick up exactly where you left off. */
@Serializable
data class QueueSnapshot(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val contextKey: String? = null,
    val contextType: String? = null,
    val contextName: String? = null,
) {
    val context: PlayContext?
        get() = if (contextKey != null && contextType != null && contextName != null)
            PlayContext(contextKey, contextType, contextName) else null
}

/** Saves the queue to app storage (one small JSON file) and reads it back. */
object QueueStore {
    /** Where the current queue came from; set by [PlayerController], saved by the service. */
    @Volatile
    var currentContext: PlayContext? = null

    private fun file(context: Context) = File(context.filesDir, "queue.json")

    fun save(context: Context, snapshot: QueueSnapshot) {
        runCatching {
            val tmp = File(context.filesDir, "queue.json.tmp")
            tmp.writeText(Net.json.encodeToString(QueueSnapshot.serializer(), snapshot))
            tmp.renameTo(file(context))
        }
    }

    fun load(context: Context): QueueSnapshot? = runCatching {
        val f = file(context)
        if (!f.exists()) null
        else Net.json.decodeFromString(QueueSnapshot.serializer(), f.readText())
    }.getOrNull()?.takeIf { it.tracks.isNotEmpty() }

    fun clear(context: Context) {
        file(context).delete()
    }
}
