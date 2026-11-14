package app.captunes.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.captunes.playback.PlayerState
import app.captunes.ui.components.formatTime
import app.captunes.ui.theme.Coral

@Composable
fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = (fraction * duration).toLong()

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            Text(
                "-" + formatTime(duration - shownMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun TransportControls(
    state: PlayerState,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle) {
            Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) Coral else Color.White)
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Box(
            Modifier
                .size(68.dp)
                .background(Color.White, CircleShape)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.buffering && state.playWhenReady) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (state.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playWhenReady) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        IconButton(onClick = onRepeat) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = when (state.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    else -> "Repeat off"
                },
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Color.White else Coral,
            )
        }
    }
}
