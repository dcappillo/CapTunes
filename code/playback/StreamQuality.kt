package app.captunes.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Streaming quality, chosen separately for mobile data and Wi-Fi. Lossless files (FLAC)
 * are several times the size of a good MP3, which is what makes them buffer. When a
 * bitrate is picked, the server converts each song to MP3 at that bitrate as it streams.
 * Mobile data defaults to 192 kbps and Wi-Fi to the original file; downloads are
 * always the original file.
 */
object StreamQuality {
    /** 0 means "original quality, never convert". */
    const val ORIGINAL = 0
    val OPTIONS = listOf(ORIGINAL, 320, 192, 128)
    private const val DEFAULT = 192
    private const val PREFS = "captunes_settings"
    private const val KEY = "cellularKbps"
    private const val KEY_WIFI = "wifiKbps"

    fun cellularKbps(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT)

    fun setCellularKbps(context: Context, kbps: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, kbps).apply()
    }

    fun wifiKbps(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_WIFI, ORIGINAL)

    fun setWifiKbps(context: Context, kbps: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_WIFI, kbps).apply()
    }

    fun label(kbps: Int): String = if (kbps == ORIGINAL) "Original" else "$kbps kbps"

    /** The setting after [kbps] in the cycle Original → 320 → 192 → 128 → Original. */
    fun next(kbps: Int): Int = OPTIONS[(OPTIONS.indexOf(kbps).coerceAtLeast(0) + 1) % OPTIONS.size]

    /** True on mobile data (or any connection Android marks as metered, like a phone hotspot). */
    fun onMobileData(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

    /**
     * Rewrites a song's stream address just before it's opened, so the choice follows
     * the connection you're on at that moment rather than when the queue was built.
     */
    @OptIn(UnstableApi::class)
    fun resolver(context: Context): ResolvingDataSource.Resolver = Resolver(context.applicationContext)

    @OptIn(UnstableApi::class)
    private class Resolver(private val app: Context) : ResolvingDataSource.Resolver {
        /** Song id → bitrate chosen when the song was first opened (0 = original). */
        private val chosen = ConcurrentHashMap<String, Int>()

        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (uri.scheme != "http" && uri.scheme != "https") return dataSpec // files on the phone
            if (uri.path?.endsWith("/rest/stream") != true) return dataSpec
            val id = uri.getQueryParameter("id") ?: return dataSpec
            // Files the phone can't play (WMA, APE…) already ask the server for a 320 kbps MP3.
            val alreadyConverting = uri.getQueryParameter("format") != null

            // Decide once per song, at its start. Later reads (seeking, reconnecting) must keep
            // the same choice, or the player would get bytes from two different files.
            val kbps = if (dataSpec.position == 0L) {
                val now = when {
                    Downloads.isDownloaded(app, id) -> ORIGINAL // play the downloaded file as it is
                    onMobileData(app) -> cellularKbps(app)
                    else -> wifiKbps(app)
                }
                if (chosen.size > 500) chosen.clear()
                chosen[id] = now
                now
            } else {
                chosen[id] ?: ORIGINAL
            }
            if (kbps == ORIGINAL || (alreadyConverting && kbps >= 320)) return dataSpec

            val converted = if (alreadyConverting) {
                // Keep everything, but lower the bitrate the server was asked for.
                val b = uri.buildUpon().clearQuery()
                for (name in uri.queryParameterNames) {
                    b.appendQueryParameter(name, if (name == "maxBitRate") "$kbps" else uri.getQueryParameter(name))
                }
                b.build()
            } else {
                uri.buildUpon()
                    .appendQueryParameter("format", "mp3")
                    .appendQueryParameter("maxBitRate", "$kbps")
                    .appendQueryParameter("estimateContentLength", "true")
                    .build()
            }
            // Its own cache key, so it never mixes with downloaded bytes of the original.
            return dataSpec.buildUpon().setUri(converted).setKey("$id:$kbps").build()
        }
    }
}
