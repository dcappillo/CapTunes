package app.captunes.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.captunes.data.Track
import app.captunes.playback.PlayerState
import app.captunes.playback.QueueEntry
import app.captunes.ui.components.Cover
import app.captunes.ui.components.EqualizerBars
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Surface1
import app.captunes.ui.theme.Surface2
import app.captunes.ui.theme.TextDim
import kotlin.math.roundToInt

private val RowHeight = 64.dp

/** Up next: tap to jump, drag the handle to reorder, swipe left to remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("Now playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            state.current?.let { QueueRow(it, playing = true, isPlaying = state.playWhenReady, handle = null) }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Next from: ${state.context?.name ?: "your queue"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.upNext.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear", color = Coral) }
            }
            if (state.shuffle) {
                Text(
                    "Shuffle is on, so the list shows shuffled order. Turn shuffle off to reorder.",
                    color = TextDim, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (state.upNext.isEmpty()) {
                Text("Nothing queued after this song.", color = TextDim, modifier = Modifier.padding(20.dp))
            } else {
                UpNextList(state.upNext, canReorder = !state.shuffle, onJump, onRemove, onMove)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextList(
    items: List<QueueEntry>,
    canReorder: Boolean,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestItems by rememberUpdatedState(items)
    val latestMove by rememberUpdatedState(onMove)

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, e -> "${e.index}:${e.track.id}" }) { pos, entry ->
            val dismiss = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onRemove(entry.index)
                        true
                    } else false
                },
            )
            val isDragging = dragging == pos
            SwipeToDismissBox(
                state = dismiss,
                enableDismissFromStartToEnd = false,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f },
                backgroundContent = {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFFB3261E)).padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White) }
                },
            ) {
                val handle = if (!canReorder) null else Modifier.pointerInput(pos) {
                    detectDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragging = pos
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                        },
                        onDragEnd = {
                            val list = latestItems
                            val target = (pos + (dragOffset / rowPx).roundToInt()).coerceIn(0, list.lastIndex)
                            if (target != pos) latestMove(list[pos].index, list[target].index)
                            dragging = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            dragging = null
                            dragOffset = 0f
                        },
                    )
                }
                QueueRow(
                    entry.track, playing = false, isPlaying = false, handle = handle,
                    modifier = Modifier
                        .background(if (isDragging) Surface2 else Surface1)
                        .clickable { onJump(entry.index) },
                )
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    playing: Boolean,
    isPlaying: Boolean,
    handle: Modifier?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(RowHeight).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.coverArt, track.albumId.hashCode(), track.album, Modifier.size(44.dp), RoundedCornerShape(4.dp), 150)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    EqualizerBars(isPlaying, Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    track.title, color = if (playing) Coral else Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(track.artist, color = TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (handle != null) {
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = TextDim)
            }
        }
    }
}
