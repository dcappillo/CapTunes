package app.captunes.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.captunes.playback.StreamQuality
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.rememberArtColor
import app.captunes.ui.components.shareTrack
import app.captunes.ui.player.MiniPlayer
import app.captunes.ui.player.NowPlayingScreen
import app.captunes.ui.theme.Night

private val NavBarHeight = 80.dp
private val MiniPlayerSpace = 68.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppShell(vm: MainViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val player by vm.player.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val sheetTrack by vm.sheetTrack.collectAsStateWithLifecycle()
    val sheetSpot by vm.sheetSpot.collectAsStateWithLifecycle()
    val showDevices by vm.showDevices.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val cellularKbps by vm.cellularKbps.collectAsStateWithLifecycle()
    val wifiKbps by vm.wifiKbps.collectAsStateWithLifecycle()
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val lookupsAsked by vm.lookupsAsked.collectAsStateWithLifecycle()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var account by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val backStack by nav.currentBackStackEntryAsState()
    val immersive = backStack?.destination?.route?.startsWith("wrapped") == true
    val current = player.current
    val hasTrack = current != null && !immersive
    val artColor by rememberArtColor(current?.coverArt, current?.albumId.hashCode())
    val miniColor = lerp(artColor, Color.Black, 0.15f)
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(hasTrack) { if (!hasTrack) expanded = false }
    // Keep the highlighted tab in sync with back presses.
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { entry ->
            Tab.entries.firstOrNull { it.route == entry.destination.route }?.let { tab = it }
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Night,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!immersive) {
                        BottomBar(tab) { selected ->
                            tab = selected
                            nav.selectTab(selected)
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(snackbar, Modifier.padding(bottom = if (hasTrack) MiniPlayerSpace else 0.dp))
                },
            ) { padding ->
                CapTunesNavHost(
                    nav = nav,
                    vm = vm,
                    onAccount = { account = true },
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding() + if (hasTrack) MiniPlayerSpace else 0.dp,
                    ),
                )
            }

            AnimatedContent(
                targetState = expanded && hasTrack,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
                label = "player",
            ) { isExpanded ->
                if (isExpanded) {
                    NowPlayingScreen(
                        vm = vm,
                        state = player,
                        liked = current != null && current.id in likedIds,
                        color = artColor,
                        shared = this@SharedTransitionLayout,
                        anim = this@AnimatedContent,
                        onCollapse = { expanded = false },
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        if (hasTrack) MiniPlayer(
                            state = player,
                            color = miniColor,
                            shared = this@SharedTransitionLayout,
                            anim = this@AnimatedContent,
                            onExpand = { expanded = true },
                            onToggle = vm::togglePlay,
                            onDevices = { vm.setDevicesVisible(true) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = NavBarHeight + navInset + 4.dp),
                        )
                    }
                }
            }
        }
    }

    sheetTrack?.let { t ->
        TrackActionsSheet(
            track = t,
            liked = t.id in likedIds,
            playlists = library.playlists,
            onDismiss = vm::closeActions,
            onLike = { vm.toggleLike(t) },
            onPlayNext = { vm.playNext(t) },
            onQueue = { vm.addToQueue(listOf(t)) },
            onAddToPlaylist = { vm.addToPlaylist(it, t) },
            onNewPlaylist = { name -> vm.createPlaylist(name, listOf(t)) },
            onAlbum = t.albumId?.let { id -> { expanded = false; nav.openAlbum(id) } },
            onArtist = t.artistId?.let { id -> { expanded = false; nav.openArtist(id) } },
            onShare = { shareTrack(context, t) },
            onDislike = { vm.dislike(t) },
            downloaded = downloads[t.id]?.done == true,
            onToggleDownload = if (session?.isLocal == true) null else {
                { if (downloads[t.id]?.done == true) vm.removeDownloads(listOf(t)) else vm.download(listOf(t)) }
            },
            onRemoveFromPlaylist = sheetSpot?.let { spot -> { vm.removeFromPlaylist(spot.playlistId, spot.index, t) } },
        )
    }
    if (showDevices) DevicesSheet(onDismiss = { vm.setDevicesVisible(false) })
    if (account) {
        AccountSheet(
            user = session?.user.orEmpty(),
            server = session?.server.orEmpty(),
            wifiOnly = wifiOnly,
            cellularQuality = StreamQuality.label(cellularKbps),
            onCellularQuality = vm::cycleCellularQuality,
            wifiQuality = StreamQuality.label(wifiKbps),
            onWifiQuality = vm::cycleWifiQuality,
            storageMb = remember { vm.storageUsedMb() },
            onWifiOnly = vm::setWifiOnly,
            onRemoveDownloads = vm::removeAllDownloads,
            onDismiss = { account = false },
            onRefresh = vm::refresh,
            onSignOut = vm::signOut,
            local = session?.isLocal == true,
            lookups = lookups,
            onLookups = vm::setLookups,
            onEraseHistory = vm::eraseHistory,
        )
    }

    // Asked once, the first time you're signed in. Nothing is sent until you say yes.
    if (session != null && !lookupsAsked) {
        AlertDialog(
            onDismissRequest = { vm.setLookups(false) },
            title = { Text("Look up lyrics and popularity online?") },
            text = {
                Text(
                    "Cap Tunes can fetch synced lyrics from LRCLIB and world popularity from Deezer. " +
                        "To do that it sends them the title, artist, album and length of songs you play. " +
                        "You can change this any time in Account.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.setLookups(true) }) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { vm.setLookups(false) }) { Text("Not now") } },
        )
    }
}
