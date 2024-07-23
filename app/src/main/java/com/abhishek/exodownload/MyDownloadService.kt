package com.abhishek.exodownload

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import com.abhishek.exodownload.Constants.PLAY_DOWNLOAD
import com.abhishek.exodownload.DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import com.abhishek.previewexoplayer.R

private const val JOB_ID = 8888
private const val FOREGROUND_NOTIFICATION_ID = 8989

@androidx.media3.common.util.UnstableApi
class MyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.exo_download_notification_channel_name, 0
) {
    override fun getDownloadManager(): DownloadManager {

        val downloadManager: DownloadManager = DownloadUtil.getDownloadManager(this)
        val downloadNotificationHelper: DownloadNotificationHelper =
            DownloadUtil.getDownloadNotificationHelper(this)
        downloadManager.addListener(
            TerminalStateNotificationHelper(
                this,
                downloadNotificationHelper,
                FOREGROUND_NOTIFICATION_ID + 1
            )
        )
        return downloadManager
    }

    override fun getScheduler(): PlatformScheduler? {
        return if (Util.SDK_INT >= 21) PlatformScheduler(this, JOB_ID) else null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val downloadIds = downloads.joinToString(", ") {
            it.request.id.toMediaItem().title
        }
        val notificationText = "$downloadIds"
        return DownloadUtil.getDownloadNotificationHelper(this)
            .buildProgressNotification(
                this,
                R.drawable.ic_launcher_foreground,
                null,
                notificationText,
                downloads,
                notMetRequirements
            )
    }

    /**
     * Creates and displays notifications for downloads when they complete or fail.
     *
     *
     * This helper will outlive the lifespan of a single instance of [MyDownloadService].
     * It is static to avoid leaking the first [MyDownloadService] instance.
     */
    private class TerminalStateNotificationHelper(
        context: Context,
        private val notificationHelper: DownloadNotificationHelper,
        firstNotificationId: Int
    ) : DownloadManager.Listener {
        private val context: Context = context.applicationContext
        private var nextNotificationId: Int = firstNotificationId


        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            val mediaItemTag=download.request.id.toMediaItem()

            val notification: Notification = when (download.state) {
                Download.STATE_COMPLETED -> {
                    notificationHelper.buildDownloadCompletedNotification(
                        context,
                        R.drawable.ic_launcher_foreground,
                        null,
                        mediaItemTag.title
                    )
                }
                Download.STATE_FAILED -> {
                    notificationHelper.buildDownloadFailedNotification(
                        context,
                        R.drawable.ic_launcher_foreground,  /* contentIntent = */
                        null,
                        Util.fromUtf8Bytes(download.request.data)
                    )
                }

                else -> return
            }
            NotificationUtil.setNotification(context, nextNotificationId++, notification)
        }
    }
    companion object {
        fun createContentIntent(context: Context, activityClass: Class<*>): PendingIntent {
            val intent = Intent(context, activityClass)
            intent.putExtra(PLAY_DOWNLOAD, PLAY_DOWNLOAD)
            return PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

}
