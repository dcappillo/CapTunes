package app.captunes.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.captunes.data.Track
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Night
import app.captunes.ui.theme.TextDim

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    downloaded: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            Cover(track.coverArt, track.albumId.hashCode(), track.album, Modifier.size(48.dp), sizePx = 150)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    EqualizerBars(isPlaying, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    track.title,
                    color = if (isCurrent) Coral else Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(Icons.Default.ArrowCircleDown, contentDescription = "Downloaded", tint = Coral, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (track.explicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    track.artist,
                    color = TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = TextDim)
        }
    }
}

@Composable
fun ExplicitBadge() {
    Box(
        Modifier
            .background(TextDim, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("E", color = Night, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** Three bouncing bars marking the song that is playing. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Coral) {
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "a")
    val b by transition.animateFloat(0.9f, 0.3f, infiniteRepeatable(tween(460), RepeatMode.Reverse), label = "b")
    val c by transition.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(330), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val heights = if (playing) listOf(a, b, c) else listOf(0.35f, 0.6f, 0.45f)
        val barW = size.width / 5f
        heights.forEachIndexed { i, f ->
            val h = size.height * f
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barW * 2f, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun CenteredMessage(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = TextDim, style = MaterialTheme.typography.bodyMedium)
        action?.invoke()
    }
}
