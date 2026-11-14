package app.captunes.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.captunes.data.Graph
import app.captunes.data.Outcome
import app.captunes.data.PlayLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches playback in the service and:
 *  1. reports plays to Navidrome (after half the song or 4 minutes), for wrapped.py;
 *  2. writes every play, with how it ended, to the on-device [PlayLog]
 *     that powers Wrapped and the smart mixes.
 */
class Scrobbler(context: Context, private val player: Player) : Player.Listener {

    private val log = PlayLog.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var item: MediaItem? = null
    private var startedAt = 0L
    private var listenedMs = 0L
    private var submitted = false

    fun start() {
        player.addListener(this)
        player.currentMediaItem?.let { begin(it) }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    /** Call from the service's onDestroy: logs the song in progress. */
    fun stop() {
        finish(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, sync = true)
        player.removeListener(this)
        scope.cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        finish(reason)
        mediaItem?.let { begin(it) } ?: run { item = null }
    }

    private fun begin(next: MediaItem) {
        item = next
        startedAt = System.currentTimeMillis()
        listenedMs = 0
        submitted = false
        send(next.mediaId, submission = false, time = startedAt)
    }

    /** Decides how the previous song ended and writes it to the play log. */
    private fun finish(reason: Int, sync: Boolean = false) {
        val ended = item ?: return
        item = null
        val track = ended.toTrack()
        val duration = track.durationMs.takeIf { it > 0 } ?: return
        val fraction = listenedMs.toDouble() / duration
        val outcome = when {
            fraction >= 0.8 -> Outcome.Complete
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> Outcome.Complete
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
                if (listenedMs < EARLY_SKIP_MS) Outcome.EarlySkip else Outcome.LateSkip
            else -> Outcome.Partial
        }
        // A brief accidental start that was replaced says nothing; skips always count.
        if (outcome == Outcome.Partial && listenedMs < 10_000) return
        val listened = listenedMs
        val start = startedAt
        if (sync) {
            runCatching { log.record(track, start, listened, outcome) }
        } else {
            scope.launch(Dispatchers.IO) {
                runCatching { log.record(track, start, listened, outcome) }
            }
        }
    }

    private fun tick() {
        val current = item ?: return
        if (!player.isPlaying) return
        listenedMs += TICK_MS
        if (submitted) return
        val duration = player.duration
        val halfway = duration > 0 && listenedMs >= duration / 2
        if (halfway || listenedMs >= FOUR_MINUTES) {
            submitted = true
            send(current.mediaId, submission = true, time = startedAt)
        }
    }

    private fun send(id: String, submission: Boolean, time: Long) {
        val repo = Graph.repo ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { repo.scrobble(id, submission, time) }
        }
    }

    private companion object {
        const val TICK_MS = 1000L
        const val FOUR_MINUTES = 240_000L
        const val EARLY_SKIP_MS = 30_000L
    }
}
