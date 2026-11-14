package app.captunes.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.playback.QueueEntry
import app.captunes.ui.components.Cover

/** "Up next" on the Now Playing screen: the next three songs, plus a way into the full queue. */
@Composable
fun UpNextCard(
    upNext: List<QueueEntry>,
    contextName: String?,
    onJump: (Int) -> Unit,
    onOpenQueue: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Up next", style = MaterialTheme.typography.titleMedium)
                contextName?.let {
                    Text(
                        "From $it", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open queue", color = Color.White)
            }
        }
        if (upNext.isEmpty()) {
            Text(
                "Nothing queued after this song.",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        upNext.take(3).forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onJump(entry.index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(entry.track.coverArt, entry.track.albumId.hashCode(), entry.track.album,
                    Modifier.size(44.dp), RoundedCornerShape(4.dp), 150)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.track.artist, color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        if (upNext.size > 3) {
            Text(
                "+ ${upNext.size - 3} more",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onOpenQueue).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
