package app.captunes.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.data.Card
import app.captunes.data.Kind
import app.captunes.ui.components.Cover
import app.captunes.ui.components.DownloadedCover
import app.captunes.ui.components.LikedCover
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.TextDim

@Composable
internal fun LibraryListItem(card: Card, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(card, Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextDim, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun LibraryGridItem(card: Card, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Art(card, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(card.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Art(card: Card, modifier: Modifier) {
    if (card.kind == Kind.Liked) {
        LikedCover(modifier)
    } else if (card.kind == Kind.Downloaded) {
        DownloadedCover(modifier)
    } else {
        Cover(
            card.coverArt, card.seed, card.title, modifier,
            shape = if (card.kind == Kind.Artist) CircleShape else RoundedCornerShape(4.dp),
            sizePx = 200,
        )
    }
}

@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    /** Set when renaming: starts with the current name and says "Save". */
    currentName: String? = null,
) {
    var name by remember { mutableStateOf(currentName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentName == null) "Give your playlist a name" else "Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("My playlist") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.ifBlank { currentName ?: "My playlist" }) }) {
                Text(if (currentName == null) "Create" else "Save", color = Coral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
