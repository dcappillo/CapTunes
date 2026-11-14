package app.captunes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.captunes.data.Card
import app.captunes.data.HomeData
import app.captunes.data.Load
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.Avatar
import app.captunes.ui.components.CenteredMessage
import app.captunes.ui.components.QuickPickTile
import app.captunes.ui.components.Shelf
import app.captunes.ui.components.CapTunesLogo
import app.captunes.ui.components.Wordmark
import app.captunes.ui.components.greeting
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Surface2
import app.captunes.ui.theme.Violet

private val Filters = listOf("All", "Albums", "Playlists")

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Card) -> Unit, onAccount: () -> Unit, onWrapped: (Int) -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("All") }
    val name = session?.user.orEmpty().replaceFirstChar { it.uppercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = 24.dp,
        ),
    ) {
        item(key = "header") { Header(name, onAccount) }
        item(key = "chips") { Chips(filter) { filter = it } }
        when (val state = home) {
            Load.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
            }
            is Load.Failed -> item(key = "error") {
                CenteredMessage("Can't reach your music", state.message) {
                    Button(onClick = vm::refresh) { Text("Try again") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onOpen(Card("downloaded", "Downloaded", "", null, app.captunes.data.Kind.Downloaded)) },
                    ) { Text("Play your downloads") }
                }
            }
            is Load.Ready -> item(key = "content") { HomeContent(state.data, filter, name, onOpen, onWrapped) }
        }
    }
}

@Composable
private fun Header(name: String, onAccount: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(Modifier.weight(1f))
            Avatar(name, 34.dp, Modifier.clickable(onClick = onAccount))
        }
        Spacer(Modifier.height(10.dp))
        Text(greeting(), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun Chips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filters.forEach { label ->
            FilterChip(
                selected = selected == label,
                onClick = { onSelect(label) },
                label = { Text(label) },
                shape = MaterialTheme.shapes.extraLarge,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Surface2,
                    labelColor = Color.White,
                    selectedContainerColor = Coral,
                    selectedLabelColor = Color.Black,
                ),
            )
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    filter: String,
    name: String,
    onOpen: (Card) -> Unit,
    onWrapped: (Int) -> Unit,
) {
    val showAlbums = filter != "Playlists"
    val showPlaylists = filter != "Albums"
    Column(Modifier.fillMaxWidth()) {
        if (filter == "All") QuickPicks(data.quickPicks, onOpen)
        if (filter == "All") WrappedBanner(onWrapped)
        if (showPlaylists) Shelf("Made For ${name.ifBlank { "You" }}", data.mixes, onOpen)
        if (showPlaylists) Shelf("Your mixes", data.forYou, onOpen)
        if (showPlaylists) Shelf("By decade", data.decades, onOpen)
        if (showAlbums) Shelf("Recently played", data.recent, onOpen)
        if (showAlbums) Shelf("Your top albums", data.frequent, onOpen)
        if (showPlaylists) Shelf("Your playlists", data.playlists, onOpen)
        if (showAlbums) Shelf("New in your library", data.newest, onOpen)
        if (showAlbums) Shelf("Rediscover", data.random, onOpen)
        val empty = data.recent.isEmpty() && data.newest.isEmpty() && data.playlists.isEmpty()
        if (empty) {
            CenteredMessage(
                "Your library is empty",
                "No music found yet. If your server is still scanning your library, give it a few minutes and refresh.",
            )
        }
    }
}

@Composable
private fun QuickPicks(cards: List<Card>, onOpen: (Card) -> Unit) {
    if (cards.isEmpty()) return
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { card ->
                    QuickPickTile(card, onClick = { onOpen(card) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WrappedBanner(onWrapped: (Int) -> Unit) {
    val year = remember { java.time.Year.now().value }
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Violet, Coral)))
            .clickable { onWrapped(year) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your $year Wrapped", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Your year in music so far", color = Color.White.copy(alpha = 0.85f))
        }
        CapTunesLogo(Modifier.size(40.dp))
    }
}
