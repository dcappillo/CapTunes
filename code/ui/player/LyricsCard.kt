package app.captunes.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.captunes.data.Lyrics
import app.captunes.data.Repository
import app.captunes.data.Track
import app.captunes.ui.theme.Night

sealed interface LyricsState {
    data object Loading : LyricsState
    data object Missing : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

@Composable
fun rememberLyrics(track: Track, repo: Repository?, lookupsOn: Boolean = false): LyricsState {
    // Keyed on the lookup switch too, so turning it on fetches lyrics for the current song.
    val state by produceState<LyricsState>(LyricsState.Loading, track.id, lookupsOn) {
        val lyrics = repo?.lyrics(track)
        value = if (lyrics == null || lyrics.lines.isEmpty()) LyricsState.Missing else LyricsState.Found(lyrics)
    }
    return state
}

/** Index of the line being sung at [positionMs], or -1. */
fun currentLine(lyrics: Lyrics, positionMs: Long): Int =
    if (!lyrics.synced) -1 else lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }

/** The lyrics card under the controls. Tap it to go full screen. */
@Composable
fun LyricsCard(
    state: LyricsState,
    positionMs: Long,
    color: Color,
    onExpand: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(lerp(color, Color.Black, 0.25f))
            .clickable(enabled = state is LyricsState.Found, onClick = onExpand)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state is LyricsState.Found) {
                IconButton(onClick = onExpand) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "Full-screen lyrics", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            LyricsState.Loading -> Text("Finding lyrics…", color = Color.White.copy(alpha = 0.6f))
            LyricsState.Missing -> Text(
                "No lyrics found for this song, on your server or on LRCLIB.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
            is LyricsState.Found -> LyricLines(state.lyrics, positionMs, Modifier.height(260.dp), 22.sp, onSeek)
        }
    }
}

/**
 * Just the line being sung, over the bottom of the album art on a soft fade,
 * so the cover stays clean. Tap it for full-screen lyrics.
 * Unsynced lyrics have no "current line", so they only show full screen.
 */
@Composable
fun LyricsOverlay(
    state: LyricsState,
    positionMs: Long,
    onExpand: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is LyricsState.Found || !state.lyrics.synced) return
    val index = currentLine(state.lyrics, positionMs)
    val line = state.lyrics.lines.getOrNull(index)?.text?.takeIf { it.isNotBlank() }
    Box(modifier) {
        AnimatedVisibility(
            visible = line != null,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.4f),
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    )
                    .clickable(onClick = onExpand)
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 20.dp),
            ) {
                AnimatedContent(
                    targetState = line.orEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 }) togetherWith
                            fadeOut(tween(180))
                    },
                    label = "lyricLine",
                ) { text ->
                    Text(
                        text,
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 10f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Full-screen lyrics in the song's color. */
@Composable
fun FullLyrics(
    track: Track,
    state: LyricsState,
    positionMs: Long,
    color: Color,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color, lerp(color, Night, 0.6f))))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close lyrics", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            }
            if (state is LyricsState.Found) {
                LyricLines(state.lyrics, positionMs, Modifier.weight(1f), 28.sp, onSeek)
                Text(
                    "Lyrics from ${state.lyrics.source}" + if (state.lyrics.synced) " · tap a line to jump to it" else "",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricLines(
    lyrics: Lyrics,
    positionMs: Long,
    modifier: Modifier,
    size: TextUnit,
    onSeek: (Long) -> Unit,
    onArt: Boolean = false,
) {
    val current = currentLine(lyrics, positionMs)
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Keep the line being sung about a third of the way down.
        val offsetPx = with(LocalDensity.current) { (maxHeight / (if (onArt) 2.6f else 3f)).roundToPx() }
        LaunchedEffect(current) {
            if (current >= 0) listState.animateScrollToItem(current, -offsetPx)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = maxHeight),
            // On the art, lyrics follow the song on their own; swiping down still closes the player.
            userScrollEnabled = !onArt,
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
                LyricText(
                    text = line.text,
                    active = i == current,
                    past = i < current,
                    synced = lyrics.synced,
                    size = size,
                    onArt = onArt,
                    onClick = line.startMs?.takeIf { lyrics.synced }?.let { start -> { onSeek(start) } },
                )
            }
        }
    }
}

@Composable
private fun LyricText(
    text: String,
    active: Boolean,
    past: Boolean,
    synced: Boolean,
    size: TextUnit,
    onArt: Boolean,
    onClick: (() -> Unit)?,
) {
    val target = when {
        !synced || active -> Color.White
        onArt -> Color.White.copy(alpha = if (past) 0.4f else 0.6f)
        past -> Color.White.copy(alpha = 0.55f)
        else -> Color.Black.copy(alpha = 0.55f)
    }
    val color by animateColorAsState(target, label = "lyric")
    val scale by animateFloatAsState(if (active) 1.04f else 1f, label = "lyricScale")
    val shown = remember(text) { text.ifBlank { "♪" } }
    Text(
        shown,
        color = color,
        fontSize = size,
        lineHeight = size * 1.35f,
        fontWeight = FontWeight.Bold,
        style = if (onArt) TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f)) else TextStyle.Default,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .padding(vertical = 6.dp),
    )
}
