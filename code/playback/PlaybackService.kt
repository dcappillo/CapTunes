package app.captunes.playback

import android.app.PendingIntent
import android.content.Context
import androidx.annotation.OptIn
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.captunes.MainActivity
import app.captunes.data.Graph
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the ExoPlayer. Runs as a foreground media service so music keeps
 * playing with the app in the background, and provides the media notification
 * and lock-screen controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var scrobbler: Scrobbler? = null
    private var queueSaver: QueueSaver? = null

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Downloads.init(this)

        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(Downloads.playerDataSource(this)))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback(applicationContext))
            .setSessionActivity(openApp)
            .build()

        player.addListener(ConvertOnFailure(player))
        scrobbler = Scrobbler(this, player).also { it.start() }
        queueSaver = QueueSaver(this, player).also { it.start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Swiping Cap Tunes out of recent apps stops the music and removes the
     * notification. Your queue and position are saved first (QueueSaver in
     * onDestroy), so the next launch picks up where you left off.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        queueSaver?.stop()
        scrobbler?.stop()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Safety net for songs whose file type we didn't know ahead of time (e.g. ones
     * rebuilt from the play log): if the phone can't read the file, ask Navidrome
     * for an MP3 conversion of that song and carry on playing it.
     */
    private class ConvertOnFailure(private val player: ExoPlayer) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode !in FORMAT_ERRORS) return
            val item = player.currentMediaItem ?: return
            val uri = item.localConfiguration?.uri?.toString() ?: return
            if ("format=mp3" in uri) return // already converted; nothing more to try
            val url = Graph.source?.streamUri(item.mediaId, null, forceConvert = true) ?: return
            if (url == uri) {
                // Nothing to convert with (e.g. a file on the phone): move on to the next song.
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
                return
            }
            val fixed = item.buildUpon()
                .setUri(url.toUri())
                .setCustomCacheKey(item.mediaId + ":mp3") // don't reuse the unreadable bytes
                .build()
            player.replaceMediaItem(player.currentMediaItemIndex, fixed)
            player.prepare()
            player.play()
        }

        companion object {
            private val FORMAT_ERRORS = setOf(
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
            )
        }
    }

    /** Stream URLs are rebuilt here, so they never travel between app and service. */
    private class SessionCallback(private val context: Context) : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { resolve(it) }.toMutableList())

        /** Bluetooth play button or the system "resume" card after the app was closed. */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Graph.init(context)
            val snap = QueueStore.load(context)
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            QueueStore.currentContext = snap.context
            val items = snap.tracks.map { resolve(it.toMediaItem()) }
            val index = snap.index.coerceIn(0, items.lastIndex)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, index, snap.positionMs))
        }

        private fun resolve(item: MediaItem): MediaItem {
            val suffix = item.mediaMetadata.extras?.getString(K_SUFFIX)
            val url = Graph.source?.streamUri(item.mediaId, suffix) ?: return item
            // Cache key = song id, so downloaded songs are found no matter the URL.
            return item.buildUpon().setUri(url.toUri()).setCustomCacheKey(item.mediaId).build()
        }
    }
}
