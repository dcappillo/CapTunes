package app.captunes.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.captunes.data.Card
import app.captunes.data.Kind
import app.captunes.playback.PlayContext
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.CenteredMessage
import app.captunes.ui.components.Cover
import app.captunes.ui.components.SectionTitle
import app.captunes.ui.components.TrackRow
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.TextDim

@Composable
fun SearchResults(vm: MainViewModel, onOpen: (Card) -> Unit) {
    val results by vm.search.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val nothing = !results.loading && results.songs.isEmpty() && results.artists.isEmpty() &&
        results.albums.isEmpty() && results.playlists.isEmpty()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (results.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral)
        }
        if (nothing) {
            CenteredMessage("No results for \"$query\"", "Check the spelling, or try an artist or album name.")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (results.songs.isNotEmpty()) item(key = "songsTitle") { SectionTitle("Songs") }
            itemsIndexed(results.songs, key = { _, t -> "song:${t.id}" }) { i, track ->
                TrackRow(
                    track = track,
                    isCurrent = player.current?.id == track.id,
                    isPlaying = player.playWhenReady,
                    onClick = {
                        vm.play(results.songs, i, PlayContext("search:$query", "Search", "\"$query\""))
                    },
                    onMore = { vm.openActions(track) },
                )
            }
            section("Artists", results.artists, onOpen)
            section("Albums", results.albums, onOpen)
            section("Playlists", results.playlists, onOpen)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    cards: List<Card>,
    onOpen: (Card) -> Unit,
) {
    if (cards.isEmpty()) return
    item(key = "title:$title") { SectionTitle(title) }
    items(cards, key = { "$title:${it.id}" }) { card -> ResultRow(card) { onOpen(card) } }
}

@Composable
private fun ResultRow(card: Card, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            card.coverArt, card.seed, card.title,
            Modifier.size(52.dp),
            shape = if (card.kind == Kind.Artist) CircleShape else RoundedCornerShape(4.dp),
            sizePx = 150,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextDim, maxLines = 1)
        }
    }
}
