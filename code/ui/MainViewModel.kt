package app.captunes.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.captunes.data.Card
import app.captunes.data.Graph
import app.captunes.data.HomeData
import app.captunes.data.LibraryData
import app.captunes.data.Load
import app.captunes.data.Repository
import app.captunes.data.SearchData
import app.captunes.data.Session
import app.captunes.data.SubsonicApi
import app.captunes.data.Track
import app.captunes.playback.DownloadInfo
import app.captunes.playback.Downloads
import app.captunes.playback.PlayContext
import app.captunes.playback.PlayerState
import app.captunes.playback.StreamQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LoginState(val loading: Boolean = false, val error: String? = null)

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    init {
        Graph.init(app)
        Downloads.init(app)
    }

    private val playerController = Graph.player(app)
    val player: StateFlow<PlayerState> = playerController.state

    private val _session = MutableStateFlow(Graph.session)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _login = MutableStateFlow(LoginState())
    val login: StateFlow<LoginState> = _login.asStateFlow()

    private val _home = MutableStateFlow<Load<HomeData>>(Load.Loading)
    val home: StateFlow<Load<HomeData>> = _home.asStateFlow()

    private val _library = MutableStateFlow(LibraryData())
    val library: StateFlow<LibraryData> = _library.asStateFlow()

    private val _genres = MutableStateFlow<List<Card>>(emptyList())
    val genres: StateFlow<List<Card>> = _genres.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<Track>>(emptyList())
    val likedSongs: StateFlow<List<Track>> = _likedSongs.asStateFlow()

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _search = MutableStateFlow(SearchData())
    val search: StateFlow<SearchData> = _search.asStateFlow()

    private val _sheetTrack = MutableStateFlow<Track?>(null)
    val sheetTrack: StateFlow<Track?> = _sheetTrack.asStateFlow()

    private val _showDevices = MutableStateFlow(false)
    val showDevices: StateFlow<Boolean> = _showDevices.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val repo: Repository? get() = Graph.repo

    val downloads: StateFlow<Map<String, DownloadInfo>> = Downloads.items

    private val _wifiOnly = MutableStateFlow(Downloads.wifiOnly(app))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _cellularKbps = MutableStateFlow(StreamQuality.cellularKbps(app))
    val cellularKbps: StateFlow<Int> = _cellularKbps.asStateFlow()

    private val _wifiKbps = MutableStateFlow(StreamQuality.wifiKbps(app))
    val wifiKbps: StateFlow<Int> = _wifiKbps.asStateFlow()

    /** Steps to the next Wi-Fi quality; applies from the next song. */
    fun cycleWifiQuality() {
        val next = StreamQuality.next(_wifiKbps.value)
        StreamQuality.setWifiKbps(getApplication(), next)
        _wifiKbps.value = next
    }

    /** Steps to the next mobile-data quality; applies from the next song. */
    fun cycleCellularQuality() {
        val next = StreamQuality.next(_cellularKbps.value)
        StreamQuality.setCellularKbps(getApplication(), next)
        _cellularKbps.value = next
    }

    fun download(tracks: List<Track>) {
        Downloads.download(getApplication(), tracks)
        _messages.tryEmit(if (tracks.size == 1) "Downloading" else "Downloading ${tracks.size} songs")
    }

    fun removeDownloads(tracks: List<Track>) {
        Downloads.remove(getApplication(), tracks)
        _messages.tryEmit("Removed from downloads")
    }

    fun removeAllDownloads() {
        Downloads.removeAll(getApplication())
        _messages.tryEmit("All downloads removed")
    }

    fun setWifiOnly(value: Boolean) {
        Downloads.setWifiOnly(getApplication(), value)
        _wifiOnly.value = value
    }

    fun storageUsedMb(): Long = Downloads.bytesUsed(getApplication()) / (1024 * 1024)

    init {
        if (Graph.session != null) onSignedIn()
        viewModelScope.launch {
            _query.debounce(300).collectLatest { q -> runSearch(q) }
        }
    }

    fun signIn(server: String, user: String, password: String) {
        if (server.isBlank() || user.isBlank() || password.isEmpty()) {
            _login.value = LoginState(error = "Fill in the server, username and password.")
            return
        }
        if (!Session.isSafeServer(server)) {
            _login.value = LoginState(
                error = "Use https, or your Tailscale / home-network address. " +
                    "Plain http over the internet would expose your login.",
            )
            return
        }
        _login.value = LoginState(loading = true)
        viewModelScope.launch {
            val candidate = Session.create(server, user, password)
            val result = runCatching { SubsonicApi(candidate).ping() }
            result.onSuccess {
                Graph.signIn(getApplication(), candidate)
                _login.value = LoginState()
                _session.value = candidate
                onSignedIn()
            }.onFailure {
                _login.value = LoginState(error = "Couldn't sign in: ${it.message ?: "no answer from server"}")
            }
        }
    }

    /** Use the music stored on this phone instead of a server. Call after the permission is granted. */
    fun useLocalLibrary() {
        val local = Session.local()
        Graph.signIn(getApplication(), local)
        _login.value = LoginState()
        _session.value = local
        onSignedIn()
    }

    fun localPermissionDenied() {
        _login.value = LoginState(
            error = "Cap Tunes needs access to your music and audio to play songs on this phone. " +
                "You can allow it in Settings → Apps → Cap Tunes → Permissions.",
        )
    }

    fun signOut() {
        playerController.stop()
        Graph.signOut(getApplication())
        _session.value = null
        _home.value = Load.Loading
        _library.value = LibraryData()
        _likedSongs.value = emptyList()
        _likedIds.value = emptySet()
    }

    private fun onSignedIn() {
        playerController.connect()
        refresh()
    }

    fun refresh() {
        val r = repo ?: return
        Graph.source?.invalidate()
        viewModelScope.launch {
            _home.value = Load.Loading
            _home.value = runCatching { Load.Ready(r.home()) }
                .getOrElse { Load.Failed(it.message ?: "Couldn't reach your server") }
        }
        viewModelScope.launch {
            _library.value = runCatching { r.library() }.getOrElse { LibraryData(loading = false) }
        }
        viewModelScope.launch { _genres.value = runCatching { r.genres() }.getOrDefault(emptyList()) }
        viewModelScope.launch { refreshLiked() }
    }

    private suspend fun refreshLiked() {
        val songs = runCatching { repo?.likedSongs() }.getOrNull() ?: return
        _likedSongs.value = songs
        _likedIds.update { it + songs.map { song -> song.id } }
    }

    fun setQuery(q: String) { _query.value = q }

    private suspend fun runSearch(q: String) {
        val r = repo
        if (q.isBlank() || r == null) {
            _search.value = SearchData()
            return
        }
        _search.update { it.copy(loading = true) }
        _search.value = runCatching { r.search(q.trim(), _library.value.playlists) }
            .getOrElse { SearchData() }
    }

    // ---- likes ----

    fun toggleLike(track: Track) {
        val nowLiked = track.id !in _likedIds.value
        _likedIds.update { if (nowLiked) it + track.id else it - track.id }
        _likedSongs.update { list ->
            if (nowLiked) listOf(track.copy(starred = true)) + list.filterNot { it.id == track.id }
            else list.filterNot { it.id == track.id }
        }
        playerController.markStarred(track.id, nowLiked)
        viewModelScope.launch {
            runCatching { repo?.setStarred(track.id, isAlbum = false, starred = nowLiked) }
                .onFailure { _messages.tryEmit("Couldn't update Liked Songs") }
        }
    }

    fun toggleAlbumLike(albumId: String) {
        val nowLiked = albumId !in _likedIds.value
        _likedIds.update { if (nowLiked) it + albumId else it - albumId }
        viewModelScope.launch {
            runCatching { repo?.setStarred(albumId, isAlbum = true, starred = nowLiked) }
            _messages.tryEmit(if (nowLiked) "Saved to your library" else "Removed from your library")
        }
    }

    /** "Not for me": the mixes stop picking this song; skips it if it's playing. */
    fun dislike(track: Track) {
        val r = repo ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { r.dislike(track) } }
        if (player.value.current?.id == track.id) playerController.next()
        _messages.tryEmit("Got it. Cap Tunes will play this less.")
    }

    fun seedLiked(ids: Collection<String>) { _likedIds.update { it + ids } }

    // ---- playback ----

    fun play(tracks: List<Track>, index: Int, context: PlayContext, shuffle: Boolean = false) =
        playerController.play(tracks, index, context, shuffle)

    fun togglePlay() = playerController.togglePlay()
    fun next() = playerController.next()
    fun previous() = playerController.previous()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun jumpTo(index: Int) = playerController.jumpTo(index)
    fun removeFromQueue(index: Int) = playerController.removeAt(index)
    fun moveInQueue(from: Int, to: Int) = playerController.move(from, to)
    fun clearUpNext() = playerController.clearUpNext()
    fun cycleRepeat() = playerController.cycleRepeat()

    fun playNext(track: Track) {
        playerController.playNext(track)
        _messages.tryEmit("Playing next")
    }

    fun addToQueue(tracks: List<Track>) {
        playerController.addToQueue(tracks)
        _messages.tryEmit(if (tracks.size == 1) "Added to queue" else "Added ${tracks.size} songs to queue")
    }

    // ---- playlists ----

    fun createPlaylist(name: String, songs: List<Track> = emptyList()) {
        val r = repo ?: return
        viewModelScope.launch {
            runCatching { r.createPlaylist(name.trim(), songs.map { it.id }) }
                .onSuccess {
                    _messages.tryEmit("Created \"${name.trim()}\"")
                    _library.value = runCatching { r.library() }.getOrDefault(_library.value)
                }
                .onFailure { _messages.tryEmit("Couldn't create the playlist") }
        }
    }

    fun addToPlaylist(playlist: Card, track: Track) {
        val r = repo ?: return
        viewModelScope.launch {
            runCatching { r.addToPlaylist(playlist.id, track.id) }
                .onSuccess { _messages.tryEmit("Added to ${playlist.title}") }
                .onFailure { _messages.tryEmit("Couldn't add to ${playlist.title}") }
        }
    }

    // ---- sheets ----

    fun openActions(track: Track) { _sheetTrack.value = track }
    fun closeActions() { _sheetTrack.value = null }
    fun setDevicesVisible(visible: Boolean) { _showDevices.value = visible }
    fun toast(text: String) { _messages.tryEmit(text) }
}
