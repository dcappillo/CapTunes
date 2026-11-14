package app.captunes.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.data.Track
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.Cover
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Surface1
import app.captunes.ui.theme.TextDim
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Search your library and tap songs to add them to the playlist you're looking at.
 * Songs already in the playlist show a check mark instead of a plus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(
    vm: MainViewModel,
    inPlaylist: Set<String>,
    onAdd: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // Songs added while this sheet is open, so they tick straight away.
    var justAdded by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300) // wait for a pause in typing
        results = try {
            vm.repo?.searchSongs(q).orEmpty()
        } catch (e: CancellationException) {
            throw e // a newer search took over
        } catch (e: Exception) {
            emptyList()
        }
        searching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Text(
                "Add songs",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search for a song or artist") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
            when {
                searching -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
                query.trim().length < 2 -> Hint("Type at least two letters to search your library.")
                results.isEmpty() -> Hint("No songs found for \"${query.trim()}\".")
                else -> LazyColumn {
                    items(results, key = { it.id }) { track ->
                        val added = track.id in inPlaylist || track.id in justAdded
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !added) {
                                    justAdded = justAdded + track.id
                                    onAdd(track)
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(
                                track.coverArt, track.albumId.hashCode(), track.album,
                                Modifier.size(44.dp), RoundedCornerShape(4.dp), sizePx = 120,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${track.artist} · ${track.album}",
                                    color = TextDim,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                if (added) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                contentDescription = if (added) "In this playlist" else "Add to playlist",
                                tint = if (added) Coral else TextDim,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = TextDim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}
