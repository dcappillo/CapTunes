package app.captunes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.captunes.data.Card
import app.captunes.data.Kind
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Surface2
import app.captunes.ui.theme.TextDim
import app.captunes.ui.theme.Violet

/** A titled horizontal row of cards. Hidden when empty. */
@Composable
fun Shelf(title: String, cards: List<Card>, onOpen: (Card) -> Unit) {
    if (cards.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(cards, key = { "${it.kind}:${it.id}" }) { card ->
                CardTile(card, width = 140.dp, onClick = { onOpen(card) })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun CardTile(card: Card, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val round = card.kind == Kind.Artist
    Column(modifier.width(width).clickable(onClick = onClick)) {
        Cover(
            coverArt = card.coverArt,
            seed = card.seed,
            label = card.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = if (round) CircleShape else RoundedCornerShape(6.dp),
            showLabel = card.kind == Kind.Mix || card.kind == Kind.Genre,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            card.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The "Liked Songs" cover: gradient square with a heart. */
@Composable
fun LikedCover(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Violet, Coral)), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/** The "Downloaded" cover: dark square with a coral arrow. */
@Composable
fun DownloadedCover(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF1F3A3A), Color(0xFF0E1A1A))), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.ArrowCircleDown, contentDescription = null, tint = Coral, modifier = Modifier.size(30.dp))
    }
}

/** Initial-letter avatar for the signed-in user or a playlist owner. */
@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(Coral, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            style = if (size > 30.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun QuickPickTile(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(56.dp)
            .background(Surface2, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            card.coverArt, card.seed, card.title,
            Modifier.size(56.dp),
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
            sizePx = 150,
        )
        Text(
            card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
    }
}
