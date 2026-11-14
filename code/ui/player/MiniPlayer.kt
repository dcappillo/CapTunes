package app.captunes.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.playback.PlayerState
import app.captunes.ui.components.Cover
import app.captunes.ui.theme.TextDim

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    state: PlayerState,
    color: Color,
    shared: SharedTransitionScope,
    anim: AnimatedVisibilityScope,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val haptics = LocalHapticFeedback.current
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    with(shared) {
        Box(
            modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(60.dp)
                .sharedBounds(rememberSharedContentState("player-surface"), anim)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .draggable(
                    state = rememberDraggableState { },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> if (velocity < -250f) onExpand() },
                )
                .clickable(onClick = onExpand)
                .drawBehind {
                    // thin live progress line along the bottom edge
                    val h = 2.dp.toPx()
                    drawRect(Color.White.copy(alpha = 0.2f), Offset(8.dp.toPx(), size.height - h), Size(size.width - 16.dp.toPx(), h))
                    drawRect(Color.White, Offset(8.dp.toPx(), size.height - h), Size((size.width - 16.dp.toPx()) * progress, h))
                },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(
                    track.coverArt, track.albumId.hashCode(), track.album,
                    Modifier
                        .size(44.dp)
                        .sharedElement(rememberSharedContentState("player-art"), anim),
                    shape = RoundedCornerShape(4.dp),
                    sizePx = 150,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDevices) {
                    Icon(Icons.Default.Devices, contentDescription = "Devices", tint = Color.White)
                }
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }) {
                    Icon(
                        if (state.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.playWhenReady) "Pause" else "Play",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
