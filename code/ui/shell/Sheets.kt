package app.captunes.ui.shell

import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.captunes.data.Card
import app.captunes.data.Track
import app.captunes.ui.components.Cover
import app.captunes.ui.library.NewPlaylistDialog
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Surface1
import app.captunes.ui.theme.TextDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    liked: Boolean,
    playlists: List<Card>,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: (Card) -> Unit,
    onNewPlaylist: (String) -> Unit,
    onAlbum: (() -> Unit)?,
    onArtist: (() -> Unit)?,
    onShare: () -> Unit,
    onDislike: () -> Unit,
    downloaded: Boolean,
    /** Null hides "Download" (songs on the phone are already offline). */
    onToggleDownload: (() -> Unit)?,
    /** Set when the menu was opened from inside a playlist. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val then = { action: () -> Unit -> { action(); onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(track.coverArt, track.albumId.hashCode(), track.album, Modifier.size(52.dp),
                RoundedCornerShape(4.dp), sizePx = 150)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (!picking) {
            SheetItem(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
                tint = if (liked) Coral else Color.White, onClick = then(onLike))
            SheetItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next", onClick = then(onPlayNext))
            SheetItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", onClick = then(onQueue))
            SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { picking = true }
            onRemoveFromPlaylist?.let {
                SheetItem(Icons.Default.RemoveCircleOutline, "Remove from this playlist", onClick = then(it))
            }
            onToggleDownload?.let { toggle ->
                SheetItem(
                    Icons.Default.ArrowCircleDown,
                    if (downloaded) "Remove download" else "Download",
                    tint = if (downloaded) Coral else Color.White,
                    onClick = then(toggle),
                )
            }
            onAlbum?.let { SheetItem(Icons.Default.Album, "Go to album", onClick = then(it)) }
            onArtist?.let { SheetItem(Icons.Default.Person, "Go to artist", onClick = then(it)) }
            SheetItem(Icons.Default.Share, "Share", onClick = then(onShare))
            SheetItem(Icons.Default.ThumbDown, "Not for me", tint = TextDim, onClick = then(onDislike))
        } else {
            SheetItem(Icons.Default.Add, "New playlist") { naming = true }
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAddToPlaylist(p); onDismiss() }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(p.coverArt, p.seed, p.title, Modifier.size(44.dp), RoundedCornerShape(4.dp), sizePx = 120)
                        Spacer(Modifier.width(12.dp))
                        Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (naming) {
        NewPlaylistDialog(
            onDismiss = { naming = false },
            onCreate = { name ->
                naming = false
                onNewPlaylist(name)
                onDismiss()
            },
        )
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Text("Connect to a device", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Coral)
            Spacer(Modifier.width(18.dp))
            Column {
                Text("This phone", color = Coral, style = MaterialTheme.typography.titleMedium)
                Text("Streaming from your Navidrome server", color = TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        SheetItem(Icons.Default.Bluetooth, "Bluetooth speakers and headphones") {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            onDismiss()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    user: String,
    server: String,
    wifiOnly: Boolean,
    storageMb: Long,
    onWifiOnly: (Boolean) -> Unit,
    onRemoveDownloads: () -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    local: Boolean = false,
    cellularQuality: String = "",
    onCellularQuality: () -> Unit = {},
    wifiQuality: String = "",
    onWifiQuality: () -> Unit = {},
    lookups: Boolean = false,
    onLookups: (Boolean) -> Unit = {},
    onEraseHistory: () -> Unit = {},
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(if (local) "Music on this phone" else user, style = MaterialTheme.typography.titleLarge)
            Text(
                if (local) "Songs stored on this device" else server,
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SheetItem(
            Icons.AutoMirrored.Filled.QueueMusic,
            if (local) "Rescan music on this phone" else "Refresh library",
        ) { onRefresh(); onDismiss() }
        if (!local) Row(
            Modifier.fillMaxWidth().clickable { onWifiOnly(!wifiOnly) }.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
                Text("Downloads use $storageMb MB", color = TextDim, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = wifiOnly,
                onCheckedChange = onWifiOnly,
                colors = SwitchDefaults.colors(checkedTrackColor = Coral),
            )
        }
        if (!local) {
            QualityRow(
                Icons.Default.SignalCellularAlt, "Quality on mobile data",
                "Lower quality buffers less on the road. Tap to change.",
                cellularQuality, onCellularQuality,
            )
            QualityRow(
                Icons.Default.Wifi, "Quality on Wi-Fi",
                "Lower this if large files buffer at home. Tap to change.",
                wifiQuality, onWifiQuality,
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable { onLookups(!lookups) }.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Online lyrics and popularity", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Sends the title, artist, album and length of songs you play to LRCLIB (lyrics) and Deezer (popularity).",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = lookups,
                onCheckedChange = onLookups,
                colors = SwitchDefaults.colors(checkedTrackColor = Coral),
            )
        }
        if (!local && storageMb > 0) SheetItem(Icons.Default.Delete, "Remove all downloads") { onRemoveDownloads(); onDismiss() }
        SheetItem(Icons.Default.History, "Erase listening history") { confirmErase = true }
        SheetItem(Icons.Default.Person, if (local) "Switch library" else "Sign out", tint = Coral) {
            if (local) { onSignOut(); onDismiss() } else confirmSignOut = true
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This removes your sign-in, downloaded songs, saved queue and cached cover art from this phone. " +
                        "Your listening history stays unless you erase it separately.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut(); onDismiss() }) { Text("Sign out", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase listening history?") },
            text = {
                Text(
                    "This deletes every play Cap Tunes has recorded on this phone. Your mixes and Wrapped start over, " +
                        "and it can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; onEraseHistory(); onDismiss() }) { Text("Erase", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }
}

/** A setting that steps through Original, 320, 192 and 128 kbps each time it's tapped. */
@Composable
private fun QualityRow(icon: ImageVector, title: String, hint: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, color = TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, color = Coral, style = MaterialTheme.typography.titleMedium)
    }
}
