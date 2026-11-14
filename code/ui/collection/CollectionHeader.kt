package app.captunes.ui.collection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.data.CollectionDetail
import app.captunes.data.Kind
import app.captunes.ui.components.Avatar
import app.captunes.ui.components.Cover
import app.captunes.ui.components.DownloadedCover
import app.captunes.ui.components.LikedCover
import app.captunes.ui.components.formatTotal
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.TextDim

@Composable
fun CollectionTopBar(title: String, color: Color, fraction: Float, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = fraction))
            .statusBarsPadding()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = fraction },
        )
    }
}

@Composable
fun CollectionHeader(detail: CollectionDetail, fraction: Float, onOwner: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(232.dp)
                .graphicsLayer {
                    val s = 1f - 0.25f * fraction
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.8f * fraction
                }
                .shadow(18.dp),
        ) {
            if (detail.kind == Kind.Liked) {
                LikedCover(Modifier.fillMaxSize())
            } else if (detail.kind == Kind.Downloaded) {
                DownloadedCover(Modifier.fillMaxSize())
            } else {
                Cover(
                    detail.coverArt, detail.id.hashCode(), detail.title, Modifier.fillMaxSize(),
                    sizePx = 700, showLabel = detail.coverArt == null,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(detail.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (onOwner != null) Modifier.clickable(onClick = onOwner) else Modifier,
        ) {
            Avatar(detail.owner, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(detail.owner, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(statsLine(detail), style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

private fun statsLine(d: CollectionDetail): String {
    val count = d.tracks.size
    val songs = if (count == 1) "1 song" else "%,d songs".format(count)
    val total = formatTotal(d.tracks.sumOf { it.durationMs })
    val prefix = listOfNotNull(d.typeLabel, d.year?.toString()).joinToString(" · ")
    return "$prefix · $songs · $total"
}

/** The big round coral Play/Pause button. */
@Composable
fun BigPlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (playing) 0.94f else 1f, spring(), label = "playScale")
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (enabled) Coral else TextDim, CircleShape)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = Color.Black,
            modifier = Modifier.size(30.dp),
        )
    }
}
