package app.captunes.ui.collection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowCircleDown as ArrowCircleDownOutlined
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.captunes.data.CollectionDetail
import app.captunes.data.Kind
import app.captunes.data.Track
import app.captunes.playback.DownloadInfo
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.TextDim
import kotlinx.coroutines.launch

/** Download status of a whole album or playlist. */
enum class DownloadState {
    None, InProgress, Done;

    companion object {
        fun of(tracks: List<Track>, downloads: Map<String, DownloadInfo>): DownloadState = when {
            tracks.isEmpty() -> None
            tracks.all { downloads[it.id]?.done == true } -> Done
            tracks.any { downloads[it.id]?.active == true } -> InProgress
            else -> None
        }
    }
}

@Composable
fun CollectionActions(
    detail: CollectionDetail,
    liked: Boolean,
    onLike: () -> Unit,
    onQueue: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    downloadState: DownloadState,
    onDownload: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (detail.kind == Kind.Album) HeartButton(liked, onLike)
        if (onDownload != null) IconButton(onClick = onDownload, enabled = detail.tracks.isNotEmpty()) {
            when (downloadState) {
                DownloadState.InProgress -> CircularProgressIndicator(Modifier.size(20.dp), color = Coral, strokeWidth = 2.dp)
                DownloadState.Done -> Icon(Icons.Default.ArrowCircleDown, contentDescription = "Remove download", tint = Coral)
                DownloadState.None -> Icon(Icons.Outlined.ArrowCircleDownOutlined, contentDescription = "Download", tint = TextDim)
            }
        }
        IconButton(onClick = onQueue, enabled = detail.tracks.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add all to queue", tint = TextDim)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Save as new playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    enabled = detail.tracks.isNotEmpty(),
                    onClick = {
                        menu = false
                        onSaveAsPlaylist()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add all to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    enabled = detail.tracks.isNotEmpty(),
                    onClick = {
                        menu = false
                        onQueue()
                    },
                )
            }
        }
    }
}

/** Heart that pops when toggled. */
@Composable
fun HeartButton(liked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
            scope.launch {
                scale.snapTo(0.7f)
                scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
            }
        },
        modifier = modifier,
    ) {
        Icon(
            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (liked) "Remove from Liked" else "Like",
            tint = if (liked) Coral else TextDim,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}

@Composable
fun ShuffleButton(on: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Shuffle,
            contentDescription = if (on) "Shuffle on" else "Shuffle off",
            tint = if (on) Coral else TextDim,
        )
    }
}
