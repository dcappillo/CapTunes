package app.captunes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.captunes.data.Card
import app.captunes.data.Kind
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.Avatar
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Surface2
import app.captunes.ui.theme.TextDim

private enum class Filter(val label: String) { Playlists("Playlists"), Artists("Artists"), Albums("Albums") }

/** Placeholder card that stands for "Liked Songs" at the top of the list. */
internal val LikedCard = Card("liked", "Liked Songs", "", null, Kind.Liked)
internal val DownloadedCard = Card("downloaded", "Downloaded", "", null, Kind.Downloaded)

@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (Card) -> Unit, onAccount: () -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val downloadedCount = downloads.values.count { it.done }
    val session by vm.session.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<Filter?>(null) }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    val items = remember(library, filter, alphabetical) {
        val base = when (filter) {
            Filter.Playlists -> library.playlists
            Filter.Artists -> library.artists
            Filter.Albums -> library.albums
            null -> library.playlists + library.artists + library.albums
        }
        if (alphabetical) base.sortedBy { it.title.lowercase() } else base
    }
    val showLiked = filter == null || filter == Filter.Playlists
    val likedCard = LikedCard.copy(subtitle = "Playlist · ${liked.size} songs")
    val pinned = buildList {
        if (showLiked) add(likedCard)
        if (showLiked && downloadedCount > 0) add(DownloadedCard.copy(subtitle = "On this phone · $downloadedCount songs"))
    }
    val all = pinned + items

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(session?.user.orEmpty(), 34.dp, Modifier.clickable(onClick = onAccount))
            Spacer(Modifier.width(12.dp))
            Text("Your Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Create playlist") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = if (filter == f) null else f },
                    label = { Text(f.label) },
                    shape = MaterialTheme.shapes.extraLarge,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Surface2, labelColor = Color.White,
                        selectedContainerColor = Coral, selectedLabelColor = Color.Black,
                    ),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { alphabetical = !alphabetical }) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (alphabetical) "Alphabetical" else "Recents", color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { grid = !grid }) {
                Icon(
                    if (grid) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = if (grid) "Show as list" else "Show as grid",
                    tint = TextDim,
                )
            }
        }
        when {
            library.loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }
            grid -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(all, key = { "${it.kind}:${it.id}" }) { LibraryGridItem(it) { onOpen(it) } }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (filter == Filter.Playlists) item(key = "new-playlist") {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).background(Surface2), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("New playlist", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(all, key = { "${it.kind}:${it.id}" }) { LibraryListItem(it) { onOpen(it) } }
            }
        }
    }

    if (creating) {
        NewPlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                creating = false
                vm.createPlaylist(name)
            },
        )
    }
}
