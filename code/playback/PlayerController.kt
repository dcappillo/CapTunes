package app.captunes.playback

import app.captunes.util.guarded
import android.net.Uri
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.captunes.data.Graph
import app.captunes.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the current queue came from, for "PLAYING FROM PLAYLIST · Name". */
@Immutable
data class PlayContext(val key: String, val type: String, val name: String)

@Immutable
data class PlayerState(
    val current: Track? = null,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val context: PlayContext? = null,
    val currentIndex: Int = -1,
    val upNext: List<QueueEntry> = emptyList(),
)

/** One upcoming song; [index] is its position in the player's playlist. */
@Immutable
data class QueueEntry(val index: Int, val track: Track)

/** App-side remote control for [PlaybackService], exposed as a StateFlow. */
class PlayerController(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var playContext: PlayContext? = null
    private var extending = false
    private var extendGaveUpAt = -1

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    fun connect() {
        if (controller != null || connectJob?.isActive == true) return
        connectJob = scope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val c = MediaController.Builder(appContext, token).buildAsync().await()
            c.addListener(listener)
            controller = c
            if (c.mediaItemCount == 0 && pending.isEmpty()) restore(c)
            pending.forEach { it(c) }
            pending.clear()
            sync()
            while (isActive) {
                delay(250)
                if (c.isPlaying) _state.update { it.copy(positionMs = c.currentPosition) }
            }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: run {
            pending += block
            connect()
        }
    }

    private fun sync() {
        val c = controller ?: return
        val track = c.currentMediaItem?.toTrack()
        _state.value = PlayerState(
            current = track,
            playWhenReady = c.playWhenReady,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition,
            durationMs = c.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            context = playContext,
            currentIndex = c.currentMediaItemIndex,
            upNext = upNext(c),
        )
        maybeExtend(c, _state.value.upNext.size)
    }

    /**
     * Mixes never run dry: when fewer than 5 songs are left in a mix,
     * fetch fresh picks from the same mix and add them to the end.
     */
    private fun maybeExtend(c: MediaController, remaining: Int) {
        val key = playContext?.key ?: return
        if (!key.startsWith("Mix:") || extending || remaining >= 5) return
        if (c.repeatMode != Player.REPEAT_MODE_OFF || c.mediaItemCount == 0) return
        val count = c.mediaItemCount
        if (extendGaveUpAt == count) return
        val repo = Graph.repo ?: return
        val exclude = (0 until count).mapTo(HashSet()) { c.getMediaItemAt(it).mediaId }
        extending = true
        scope.launch {
            val more = guarded("PlayerController") {
                withContext(Dispatchers.IO) { repo.moreForMix(key.removePrefix("Mix:"), exclude) }
            }.getOrDefault(emptyList())
            if (more.isEmpty()) extendGaveUpAt = count
            else if (playContext?.key == key && controller === c) c.addMediaItems(more.map { it.toMediaItem() })
            extending = false
        }
    }

    /** Songs that will play after the current one, in play order (respects shuffle). */
    private fun upNext(c: MediaController): List<QueueEntry> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<QueueEntry>()
        var i = timeline.getNextWindowIndex(c.currentMediaItemIndex, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < 500) {
            out += QueueEntry(i, c.getMediaItemAt(i).toTrack())
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    /** Puts back the last queue and position, paused, so you can pick up where you left off. */
    private fun restore(c: MediaController) {
        val snap = QueueStore.load(appContext) ?: return
        playContext = snap.context
        QueueStore.currentContext = snap.context
        c.setMediaItems(snap.tracks.map { it.toMediaItem() }, snap.index.coerceIn(snap.tracks.indices), snap.positionMs)
        c.shuffleModeEnabled = snap.shuffle
        c.repeatMode = snap.repeatMode
        c.prepare()
    }

    fun jumpTo(index: Int) = withController {
        it.seekToDefaultPosition(index)
        it.play()
    }

    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }

    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }

    fun clearUpNext() = withController { c ->
        val current = c.currentMediaItemIndex
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
        if (current > 0) c.removeMediaItems(0, current)
    }

    /** startIndex < 0 means "start anywhere" (a random song when shuffling, else the first). */
    fun play(tracks: List<Track>, startIndex: Int, context: PlayContext, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        playContext = context
        QueueStore.currentContext = context
        extendGaveUpAt = -1
        withController { c ->
            val start = when {
                startIndex >= 0 -> startIndex.coerceIn(tracks.indices)
                shuffle -> tracks.indices.random()
                else -> 0
            }
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { if (it.playWhenReady) it.pause() else it.play() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController {
        it.seekTo(ms)
        _state.update { s -> s.copy(positionMs = ms) }
    }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(track: Track) = withController { c ->
        val at = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
        c.addMediaItem(at, track.toMediaItem())
        if (c.mediaItemCount == 1) { c.prepare(); c.play() }
    }

    fun addToQueue(tracks: List<Track>) = withController { c ->
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(tracks.map { it.toMediaItem() })
        if (wasEmpty) { c.prepare(); c.play() }
    }

    /** Refreshes the heart on the current track after a like/unlike. */
    fun markStarred(id: String, starred: Boolean) {
        _state.update { s ->
            if (s.current?.id == id) s.copy(current = s.current.copy(starred = starred)) else s
        }
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
        playContext = null
        QueueStore.currentContext = null
        QueueStore.clear(appContext)
    }
}

private const val K_ARTIST_ID = "artistId"
private const val K_ALBUM_ID = "albumId"
private const val K_COVER = "coverArt"
private const val K_DURATION = "durationMs"
private const val K_EXPLICIT = "explicit"
private const val K_STARRED = "starred"
private const val K_GENRE = "genre"
private const val K_YEAR = "year"
internal const val K_SUFFIX = "suffix"

internal const val COVER_SCHEME = "captunes"

/**
 * Other apps can see a song's artwork address through the media session, and the real
 * address carries the sign-in token. So we publish a stand-in that only names the cover;
 * the playback service swaps in the real address when it loads the picture itself.
 */
private fun coverPlaceholder(coverArt: String?): Uri? =
    coverArt?.takeIf { it.isNotBlank() }?.let {
        Uri.Builder().scheme(COVER_SCHEME).authority("cover").appendQueryParameter("id", it).build()
    }

fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(coverPlaceholder(coverArt))
        .setExtras(
            bundleOf(
                K_ARTIST_ID to artistId, K_ALBUM_ID to albumId, K_COVER to coverArt,
                K_DURATION to durationMs, K_EXPLICIT to explicit, K_STARRED to starred, K_GENRE to genre, K_YEAR to (year ?: 0),
                K_SUFFIX to suffix,
            ),
        )
        .build()
    return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
}

fun MediaItem.toTrack(): Track {
    val m = mediaMetadata
    val x = m.extras
    return Track(
        id = mediaId,
        title = m.title?.toString().orEmpty(),
        artist = m.artist?.toString().orEmpty(),
        artistId = x?.getString(K_ARTIST_ID),
        album = m.albumTitle?.toString().orEmpty(),
        albumId = x?.getString(K_ALBUM_ID),
        coverArt = x?.getString(K_COVER),
        durationMs = x?.getLong(K_DURATION) ?: 0L,
        explicit = x?.getBoolean(K_EXPLICIT) ?: false,
        starred = x?.getBoolean(K_STARRED) ?: false,
        genre = x?.getString(K_GENRE),
        year = x?.getInt(K_YEAR)?.takeIf { it > 0 },
        suffix = x?.getString(K_SUFFIX),
    )
}
