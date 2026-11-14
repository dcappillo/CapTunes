package app.captunes.playback

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import app.captunes.R
import app.captunes.data.Graph

/** Keeps downloads going in the background, with a progress notification. */
@OptIn(UnstableApi::class)
class CapTunesDownloadService : DownloadService(
    NOTIFICATION_ID,
    DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val helper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager {
        Graph.init(this)
        return Downloads.manager(this)
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        helper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            "Downloading to Cap Tunes",
            downloads,
            notMetRequirements,
        )

    private companion object {
        const val NOTIFICATION_ID = 7
        const val CHANNEL_ID = "downloads"
    }
}
