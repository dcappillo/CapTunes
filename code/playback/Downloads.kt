package app.captunes.playback

import app.captunes.util.guarded
import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.captunes.data.Graph
import app.captunes.data.Net
import app.captunes.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** Download progress for one song. */
@Immutable
data class DownloadInfo(val track: Track, val state: Int, val percent: Float) {
    val done get() = state == Download.STATE_COMPLETED
    val active get() = state == Download.STATE_QUEUED || state == Download.STATE_DOWNLOADING ||
        state == Download.STATE_RESTARTING
}

/**
 * Offline downloads. Songs are saved into a private cache keyed by song id,
 * and the player reads from that cache first, so a downloaded song plays
 * with no connection at all. Streaming does not fill the cache; only downloads do.
 */
@OptIn(UnstableApi::class)
object Downloads {
    private var cache: SimpleCache? = null
    private var database: StandaloneDatabaseProvider? = null
    private var manager: DownloadManager? = null
    private val _items = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val items: StateFlow<Map<String, DownloadInfo>> = _items.asStateFlow()

    private const val PREFS = "captunes_settings"

    @Synchronized
    private fun database(context: Context): StandaloneDatabaseProvider =
        database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

    @Synchronized
    private fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.filesDir, "downloads"),
        NoOpCacheEvictor(),
        database(context),
    ).also { cache = it }

    /** Data source for the player: downloaded bytes first, network otherwise. */
    fun playerDataSource(context: Context): DataSource.Factory =
        // The quality resolver runs before the download cache is consulted. A converted stream
        // gets its own cache key, so it is never stitched onto a partly downloaded original.
        ResolvingDataSource.Factory(
            CacheDataSource.Factory()
                .setCache(cache(context))
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()))
                .setCacheWriteDataSinkFactory(null),
            StreamQuality.resolver(context),
        )

    /** True once a song is fully downloaded. Reads the download database, so it works in the service too. */
    fun isDownloaded(context: Context, songId: String): Boolean = guarded("Downloads") {
        manager(context).downloadIndex.getDownload(songId)?.state == Download.STATE_COMPLETED
    }.getOrDefault(false)

    @Synchronized
    fun manager(context: Context): DownloadManager = manager ?: DownloadManager(
        context.applicationContext,
        database(context),
        cache(context),
        DefaultHttpDataSource.Factory(),
        Executors.newFixedThreadPool(2),
    ).also { m ->
        m.maxParallelDownloads = 2
        m.requirements = requirements(wifiOnly(context))
        m.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = refresh(downloadManager)
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) =
                update(download)
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                _items.value = _items.value - download.request.id
            }
        })
        manager = m
        if (m.isInitialized) refresh(m)
    }

    /** Call once at startup so the Downloaded list is ready. */
    fun init(context: Context) {
        manager(context)
    }

    private fun refresh(m: DownloadManager) {
        val map = HashMap<String, DownloadInfo>()
        guarded("Downloads") {
            m.downloadIndex.getDownloads().use { c ->
                while (c.moveToNext()) info(c.download)?.let { map[it.track.id] = it }
            }
        }
        _items.value = map
    }

    private fun update(d: Download) {
        info(d)?.let { _items.value = _items.value + (it.track.id to it) }
    }

    private fun info(d: Download): DownloadInfo? {
        val track = guarded("Downloads") {
            Net.json.decodeFromString(Track.serializer(), String(d.request.data, Charsets.UTF_8))
        }.getOrNull() ?: return null
        return DownloadInfo(track, d.state, d.percentDownloaded.coerceAtLeast(0f))
    }

    /** Leave this much free so downloads can't fill the phone. */
    private const val MIN_FREE_BYTES = 500L * 1024 * 1024

    fun hasRoom(context: Context): Boolean =
        guarded("Downloads") { context.applicationContext.filesDir.usableSpace > MIN_FREE_BYTES }.getOrDefault(true)

    /** Returns false, without starting anything, when the phone is nearly full. */
    fun download(context: Context, tracks: List<Track>): Boolean {
        if (!hasRoom(context)) return false
        val source = Graph.source ?: return false
        tracks.filter { _items.value[it.id]?.let { i -> i.done || i.active } != true }.forEach { t ->
            val request = DownloadRequest.Builder(t.id, source.streamUri(t.id, t.suffix).toUri())
                .setCustomCacheKey(t.id)
                .setData(Net.json.encodeToString(Track.serializer(), t).toByteArray(Charsets.UTF_8))
                .build()
            DownloadService.sendAddDownload(context, CapTunesDownloadService::class.java, request, false)
        }
        return true
    }

    fun remove(context: Context, tracks: List<Track>) {
        tracks.forEach { DownloadService.sendRemoveDownload(context, CapTunesDownloadService::class.java, it.id, false) }
    }

    fun removeAll(context: Context) {
        DownloadService.sendRemoveAllDownloads(context, CapTunesDownloadService::class.java, false)
    }

    fun downloadedTracks(): List<Track> = _items.value.values.filter { it.done }.map { it.track }
        .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }))

    fun bytesUsed(context: Context): Long = guarded("Downloads") { cache(context).cacheSpace }.getOrDefault(0L)

    fun wifiOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("wifiOnly", true)

    fun setWifiOnly(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("wifiOnly", value).apply()
        manager(context).requirements = requirements(value)
    }

    private fun requirements(wifiOnly: Boolean) =
        Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
}
