package app.captunes.ui.collection

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.captunes.data.ArtistDetail
import app.captunes.data.Card
import app.captunes.data.Load
import app.captunes.playback.PlayContext
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.CardTile
import app.captunes.ui.components.CenteredMessage
import app.captunes.ui.components.Cover
import app.captunes.ui.components.rememberArtColor
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Night
import app.captunes.ui.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(vm: MainViewModel, id: String, onBack: () -> Unit, onOpen: (Card) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val load by produceState<Load<ArtistDetail>>(Load.Loading, id, attempt) {
        val repo = vm.repo
        value = if (repo == null) Load.Failed("Not signed in")
        else runCatching { Load.Ready(repo.artist(id)) }.getOrElse { Load.Failed(it.message ?: "Couldn't load artist") }
    }
    when (val state = load) {
        Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Coral)
        }
        is Load.Failed -> Column(Modifier.fillMaxSize()) {
            CollectionTopBar("", Night, 0f, onBack)
            CenteredMessage("Something went wrong", state.message) {
                Button(onClick = { attempt++ }) { Text("Try again") }
            }
        }
        is Load.Ready -> ArtistContent(state.data, vm, onBack, onOpen)
    }
}

@Composable
private fun ArtistContent(artist: ArtistDetail, vm: MainViewModel, onBack: () -> Unit, onOpen: (Card) -> Unit) {
    val color by rememberArtColor(artist.coverArt, artist.id.hashCode())
    val player by vm.player.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var loadingTracks by remember { mutableStateOf(false) }
    val key = "Artist:${artist.id}"
    val isThis = player.context?.key == key && player.current != null

    fun playArtist(shuffle: Boolean) {
        if (isThis) { vm.togglePlay(); return }
        val repo = vm.repo ?: return
        loadingTracks = true
        scope.launch {
            val tracks = runCatching { repo.artistTracks(artist.id, artist.albums) }.getOrDefault(emptyList())
            loadingTracks = false
            if (tracks.isEmpty()) vm.toast("No songs found for ${artist.name}")
            else vm.play(tracks, -1, PlayContext(key, "Artist", artist.name), shuffle)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to color, 0.45f to Night))) {
        Column(Modifier.fillMaxSize()) {
            CollectionTopBar(artist.name, color, 0f, onBack)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Cover(artist.coverArt, artist.id.hashCode(), artist.name, Modifier.size(180.dp), CircleShape)
                        Spacer(Modifier.height(16.dp))
                        Text(artist.name, style = MaterialTheme.typography.headlineMedium)
                        Text("${artist.albums.size} albums in your library", color = TextDim,
                            style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            if (loadingTracks) CircularProgressIndicator(Modifier.size(24.dp), color = Coral)
                            ShuffleButton(on = false) { playArtist(shuffle = true) }
                            BigPlayButton(isThis && player.playWhenReady, enabled = !loadingTracks) { playArtist(false) }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "albumsTitle") {
                    Text("Albums", style = MaterialTheme.typography.titleLarge)
                }
                items(artist.albums, key = { it.id }) { album ->
                    CardTile(album, width = Dp.Unspecified, onClick = { onOpen(album) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
