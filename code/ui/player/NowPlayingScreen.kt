package app.captunes.ui.player

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.data.Track
import app.captunes.playback.PlayerState
import app.captunes.ui.MainViewModel
import app.captunes.ui.collection.HeartButton
import app.captunes.ui.components.Cover
import app.captunes.ui.components.shareTrack
import app.captunes.ui.components.BoltYellow
import app.captunes.ui.theme.Night
import app.captunes.ui.theme.TextDim
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    vm: MainViewModel,
    state: PlayerState,
    liked: Boolean,
    color: Color,
    shared: SharedTransitionScope,
    anim: AnimatedVisibilityScope,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val lookupsOn by vm.lookups.collectAsStateWithLifecycle()
    var lyricsOnArt by rememberSaveable { mutableStateOf(true) }
    val lyrics = rememberLyrics(track, vm.repo, lookupsOn)
    val drag = remember { Animatable(0f) }

    // System back / predictive back gesture shrinks the sheet, then closes it.
    PredictiveBackHandler { progress ->
        try {
            progress.collect { backProgress = it.progress }
            onCollapse()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    // Back closes full-screen lyrics first.
    BackHandler(enabled = showLyrics) { showLyrics = false }

    with(shared) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = drag.value
                    val s = 1f - 0.08f * backProgress
                    scaleX = s
                    scaleY = s
                }
                .sharedBounds(rememberSharedContentState("player-surface"), anim)
                .background(Brush.verticalGradient(0f to color, 0.75f to Night))
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                // Drag this top area down to close.
                Column(
                    Modifier.draggable(
                        state = rememberDraggableState { delta ->
                            scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (drag.value > 280f || velocity > 1500f) onCollapse()
                            else drag.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        },
                    ),
                ) {
                    TopRow(state, onCollapse = onCollapse, onMore = { vm.openActions(track) })
                    Spacer(Modifier.height(24.dp))
                    Artwork(track, state.playWhenReady, shared, anim) { artModifier ->
                        if (lyricsOnArt) {
                            LyricsOverlay(lyrics, state.positionMs, onExpand = { showLyrics = true }, onSeek = vm::seekTo, modifier = artModifier)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                TitleRow(track, liked) { vm.toggleLike(track) }
                Spacer(Modifier.height(12.dp))
                SeekBar(state, vm::seekTo)
                TransportControls(
                    state,
                    onShuffle = vm::toggleShuffle,
                    onPrevious = vm::previous,
                    onToggle = vm::togglePlay,
                    onNext = vm::next,
                    onRepeat = vm::cycleRepeat,
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setDevicesVisible(true) }) {
                        Icon(Icons.Default.Devices, contentDescription = "Devices", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    val hasLyrics = lyrics is LyricsState.Found
                    IconButton(onClick = { lyricsOnArt = !lyricsOnArt }, enabled = hasLyrics) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = if (lyricsOnArt) "Hide lyrics on the cover" else "Show lyrics on the cover",
                            tint = when {
                                !hasLyrics -> Color.White.copy(alpha = 0.3f)
                                lyricsOnArt -> BoltYellow
                                else -> Color.White
                            },
                        )
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                    }
                    IconButton(onClick = { shareTrack(context, track) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
                UpNextCard(
                    upNext = state.upNext,
                    contextName = state.context?.name,
                    onJump = vm::jumpTo,
                    onOpenQueue = { showQueue = true },
                )
                if (lyrics is LyricsState.Missing) {
                    Text(
                        if (lookupsOn) "No lyrics found for this song."
                        else "No lyrics on your server for this song. Turn on online lyrics in Account to search for them.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
            if (showLyrics) {
                FullLyrics(track, lyrics, state.positionMs, color, onClose = { showLyrics = false }, onSeek = vm::seekTo)
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            state = state,
            onDismiss = { showQueue = false },
            onJump = vm::jumpTo,
            onRemove = vm::removeFromQueue,
            onMove = vm::moveInQueue,
            onClear = vm::clearUpNext,
        )
    }
}

@Composable
private fun TopRow(state: PlayerState, onCollapse: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player", tint = Color.White)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val ctx = state.context
            Text(
                "PLAYING FROM ${(ctx?.type ?: "your library").uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                ctx?.name ?: "Cap Tunes",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Artwork(
    track: Track,
    playing: Boolean,
    shared: SharedTransitionScope,
    anim: AnimatedVisibilityScope,
    overlay: @Composable BoxScope.(Modifier) -> Unit,
) {
    val scale by animateFloatAsState(
        if (playing) 1f else 0.88f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "artScale",
    )
    with(shared) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Cover(
                track.coverArt, track.albumId.hashCode(), track.album,
                Modifier
                    .fillMaxSize()
                    .sharedElement(rememberSharedContentState("player-art"), anim)
                    .shadow(24.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                sizePx = 900,
            )
            overlay(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
private fun TitleRow(track: Track, liked: Boolean, onLike: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = TextDim, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        HeartButton(liked, onLike)
    }
}
