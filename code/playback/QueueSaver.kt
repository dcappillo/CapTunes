package app.captunes.playback

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps [QueueStore] up to date from inside the playback service:
 * on every song change, queue edit or pause, plus every 10 s while playing.
 */
class QueueSaver(private val context: Context, private val player: Player) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: Job? = null

    fun start() {
        player.addListener(this)
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) saveNow()
            }
        }
    }

    fun stop() {
        saveNow(sync = true)
        player.removeListener(this)
        scope.cancel()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            // Debounce bursts (a new queue fires several events at once).
            pending?.cancel()
            pending = scope.launch {
                delay(500)
                saveNow()
            }
        }
    }

    private fun saveNow(sync: Boolean = false) {
        val count = player.mediaItemCount
        if (count == 0) return
        val snapshot = QueueSnapshot(
            tracks = (0 until count).map { player.getMediaItemAt(it).toTrack() },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            contextKey = QueueStore.currentContext?.key,
            contextType = QueueStore.currentContext?.type,
            contextName = QueueStore.currentContext?.name,
        )
        if (sync) QueueStore.save(context, snapshot)
        else scope.launch(Dispatchers.IO) { QueueStore.save(context, snapshot) }
    }
}
