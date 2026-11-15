package app.captunes.ui.collection

import app.captunes.util.guarded
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import app.captunes.ui.library.NewPlaylistDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.captunes.data.CollectionDetail
import app.captunes.data.Kind
import app.captunes.data.Load
import app.captunes.playback.PlayContext
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.CenteredMessage
import app.captunes.ui.components.TrackRow
import app.captunes.ui.components.rememberArtColor
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Night
import androidx.compose.material3.Text

@Composable
fun CollectionScreen(
    vm: MainViewModel,
    kind: Kind,
    id: String,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    // Playlists reload whenever one is edited (songs added or removed, renamed).
    val playlistVersion by vm.playlistVersion.collectAsStateWithLifecycle()
    val edits = if (kind == Kind.Playlist) playlistVersion else 0
    val load by produceState<Load<CollectionDetail>>(Load.Loading, kind, id, attempt, edits) {
        val repo = vm.repo
        value = if (repo == null) Load.Failed("Not signed in")
        else guarded("CollectionScreen") { Load.Ready(repo.collection(kind, id)) }
            .getOrElse { Load.Failed(it.message ?: "Couldn't load this") }
    }
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()

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
        is Load.Ready -> {
            val detail = when (kind) {
                Kind.Liked -> state.data.copy(tracks = liked)
                Kind.Downloaded -> state.data.copy(
                    tracks = downloads.values.filter { it.done }.map { it.track }
                        .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() })),
                )
                else -> state.data
            }
            LaunchedEffect(detail.id) { if (detail.starred) vm.seedLiked(listOf(detail.id)) }
            CollectionContent(detail, vm, onBack, onArtist)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionContent(
    detail: CollectionDetail,
    vm: MainViewModel,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
) {
    val player by vm.player.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val local = session?.isLocal == true
    val downloadState = remember(detail.tracks, downloads) { DownloadState.of(detail.tracks, downloads) }
    val artColor by rememberArtColor(detail.coverArt, detail.id.hashCode())
    val listState = rememberLazyListState()
    val headerPx = with(LocalDensity.current) { 300.dp.toPx() }
    val fraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / headerPx).coerceIn(0f, 1f)
        }
    }
    val stuck by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val stickyColor by animateColorAsState(if (stuck) Night else Color.Transparent, label = "sticky")

    val contextKey = "${detail.kind}:${detail.id}"
    val playContext = PlayContext(contextKey, detail.typeLabel, detail.title)
    val isThis = player.context?.key == contextKey && player.current != null
    var shuffle by rememberSaveable(contextKey) { mutableStateOf(false) }
    // Playlists shared by someone else on the server can be played but not changed.
    val editable = detail.kind == Kind.Playlist &&
        (local || detail.owner == "You" || detail.owner.equals(session?.user, ignoreCase = true))
    var addingSongs by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Night)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .graphicsLayer { alpha = 1f - 0.6f * fraction }
                .background(Brush.verticalGradient(listOf(artColor, Night))),
        )
        Column(Modifier.fillMaxSize()) {
            CollectionTopBar(detail.title, artColor, fraction, onBack)
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header") {
                    val ownerId = detail.ownerId
                    CollectionHeader(detail, fraction, ownerId?.let { oid -> { onArtist(oid) } })
                }
                stickyHeader(key = "actions") {
                    ActionRow(
                        detail = detail,
                        liked = detail.id in likedIds,
                        shuffle = if (isThis) player.shuffle else shuffle,
                        playing = isThis && player.playWhenReady,
                        modifier = Modifier.background(stickyColor),
                        onLike = { vm.toggleAlbumLike(detail.id) },
                        onQueue = { vm.addToQueue(detail.tracks) },
                        onSaveAsPlaylist = { vm.createPlaylist(detail.title, detail.tracks) },
                        downloadState = downloadState,
                        onDownload = if (local) null else {
                            {
                                if (downloadState == DownloadState.Done) vm.removeDownloads(detail.tracks)
                                else vm.download(detail.tracks)
                            }
                        },
                        onAddSongs = if (editable) ({ addingSongs = true }) else null,
                        onRename = if (editable) ({ renaming = true }) else null,
                        onDelete = if (editable) ({ confirmDelete = true }) else null,
                        onShuffle = {
                            shuffle = !shuffle
                            if (isThis) vm.toggleShuffle()
                        },
                        onPlay = {
                            if (isThis) vm.togglePlay()
                            else vm.play(detail.tracks, -1, playContext, shuffle)
                        },
                    )
                }
                if (detail.tracks.isEmpty()) {
                    item(key = "empty") {
                        if (detail.kind == Kind.Liked) {
                            CenteredMessage("Songs you like will appear here", "Tap the heart on any song to save it.")
                        } else if (editable) {
                            CenteredMessage("This playlist is empty", "Add the first songs to get it started.") {
                                Button(onClick = { addingSongs = true }) { Text("Add songs") }
                            }
                        } else {
                            CenteredMessage("Nothing here yet", "This list doesn't have any songs.")
                        }
                    }
                }
                itemsIndexed(detail.tracks, key = { i, t -> "$i:${t.id}" }) { i, track ->
                    TrackRow(
                        track = track,
                        isCurrent = player.current?.id == track.id,
                        isPlaying = player.playWhenReady,
                        showArt = detail.kind != Kind.Album,
                        downloaded = downloads[track.id]?.done == true,
                        onClick = { vm.play(detail.tracks, i, playContext, shuffle) },
                        onMore = {
                            if (editable) vm.openActions(track, detail.id, i) else vm.openActions(track)
                        },
                    )
                }
                item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (addingSongs) {
        AddSongsSheet(
            vm = vm,
            inPlaylist = remember(detail.tracks) { detail.tracks.mapTo(HashSet()) { it.id } },
            onAdd = { vm.addToPlaylist(detail.id, detail.title, it) },
            onDismiss = { addingSongs = false },
        )
    }
    if (renaming) {
        NewPlaylistDialog(
            onDismiss = { renaming = false },
            onCreate = { name ->
                renaming = false
                vm.renamePlaylist(detail.id, name)
            },
            currentName = detail.title,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${detail.title}\"?") },
            text = { Text("The playlist is removed for good. The songs stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Leave the screen now; if the server is slow you may have moved on by the time it answers.
                    onBack()
                    vm.deletePlaylist(detail.id, detail.title)
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(
    detail: CollectionDetail,
    liked: Boolean,
    shuffle: Boolean,
    playing: Boolean,
    modifier: Modifier,
    onLike: () -> Unit,
    onQueue: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    downloadState: DownloadState,
    onDownload: (() -> Unit)?,
    onAddSongs: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionActions(
            detail, liked, onLike, onQueue, onSaveAsPlaylist, downloadState, onDownload,
            onAddSongs, onRename, onDelete,
        )
        Spacer(Modifier.weight(1f))
        ShuffleButton(shuffle, onShuffle)
        Spacer(Modifier.padding(4.dp))
        BigPlayButton(playing, enabled = detail.tracks.isNotEmpty(), onClick = onPlay)
        Spacer(Modifier.padding(4.dp))
    }
}
