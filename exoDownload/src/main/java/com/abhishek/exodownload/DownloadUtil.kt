package com.abhishek.exodownload


import  android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.chromium.net.CronetEngine
import java.io.File
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
object DownloadUtil {
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    private const val TAG = "DownloadUtil"
    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"

    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var downloadCache: Cache
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var httpDataSourceFactory: HttpDataSource.Factory
    private lateinit var downloadNotificationHelper: DownloadNotificationHelper
    private lateinit var downloadDirectory: File
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadTracker: DownloadTracker

    @Synchronized
    fun getHttpDataSourceFactory(context: Context): HttpDataSource.Factory {
        if (!DownloadUtil::httpDataSourceFactory.isInitialized) {
            httpDataSourceFactory = CronetDataSource.Factory(
                CronetEngine.Builder(context).build(),
                Executors.newSingleThreadExecutor()
            )
        }
        return httpDataSourceFactory
    }

    @Synchronized
    fun getReadOnlyDataSourceFactory(context: Context): DataSource.Factory {
        if (!DownloadUtil::dataSourceFactory.isInitialized) {
            val contextApplication = context.applicationContext
            val upstreamFactory = DefaultDataSource.Factory(
                contextApplication,
                getHttpDataSourceFactory(contextApplication)
            )
            dataSourceFactory =
                buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(contextApplication))
        }
        return dataSourceFactory
    }

    @Synchronized
    fun getDownloadNotificationHelper(context: Context?): DownloadNotificationHelper {
        if (!DownloadUtil::downloadNotificationHelper.isInitialized) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context!!, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        ensureDownloadManagerInitialized(context)
        return downloadManager
    }

    @Synchronized
    fun getDownloadTracker(context: Context): DownloadTracker {
        ensureDownloadManagerInitialized(context)
        return downloadTracker
    }

    fun getDownloadString(context: Context, @Download.State downloadState: Int): String {
        return when (downloadState) {
            Download.STATE_COMPLETED -> context.resources.getString(R.string.exo_download_completed)
            Download.STATE_DOWNLOADING -> context.resources.getString(R.string.exo_download_downloading)
            Download.STATE_FAILED -> context.resources.getString(R.string.exo_download_failed)
            Download.STATE_QUEUED -> context.resources.getString(R.string.exo_download_queued)
            Download.STATE_REMOVING -> context.resources.getString(R.string.exo_download_removing)
            Download.STATE_RESTARTING -> context.resources.getString(R.string.exo_download_restarting)
            Download.STATE_STOPPED -> context.resources.getString(R.string.exo_download_stopped)
            else -> throw IllegalArgumentException()
        }
    }

    @Synchronized
    private fun getDownloadCache(context: Context): Cache {
        if (!DownloadUtil::downloadCache.isInitialized) {
            val downloadContentDirectory =
                File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY)
            downloadCache = SimpleCache(
                downloadContentDirectory,
                NoOpCacheEvictor(),
                getDatabaseProvider(context)
            )
        }
        return downloadCache
    }

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        if (!DownloadUtil::downloadManager.isInitialized) {
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getDownloadCache(context),
                getHttpDataSourceFactory(context),
                Executors.newFixedThreadPool(6)
            ).apply {
                maxParallelDownloads = 2
            }
            downloadTracker =
                DownloadTracker(context, getHttpDataSourceFactory(context), downloadManager)
        }
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (!DownloadUtil::databaseProvider.isInitialized) databaseProvider =
            StandaloneDatabaseProvider(context)
        return databaseProvider
    }

    @Synchronized
    fun getDownloadDirectory(context: Context): File {
        if (!DownloadUtil::downloadDirectory.isInitialized) {
            val state = Environment.getExternalStorageDirectory().absolutePath
            downloadDirectory = if (Environment.MEDIA_MOUNTED == state) {
                context.getExternalFilesDir(null)!!
            } else {
                context.filesDir
            }
        }
        return downloadDirectory
    }

    private fun buildReadOnlyCacheDataSource(
        upstreamFactory: DataSource.Factory,
        cache: Cache
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createMediaSource(context: Context, mediaItem: MediaItem): MediaSource {
        val dataSource = getHttpDataSourceFactory(context)
        return when (mediaItem.localConfiguration?.mimeType) {
            MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(dataSource)
            MimeTypes.APPLICATION_MP4 -> ProgressiveMediaSource.Factory(dataSource)
            else -> throw IllegalArgumentException("Unknown mimeType ${mediaItem.localConfiguration?.mimeType}")
        }.createMediaSource(mediaItem)
    }

    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String,
        positiveAction: () -> Unit,
        negativeButtonText: String,
        negativeAction: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context, R.style.MaterialAlertDialog_rounded)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ ->
                positiveAction()
            }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                dialog.dismiss()
                negativeAction()
            }
            .show().also { dialog ->
                // Change the color of the "No" button
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                    ?.setTextColor(
                        ContextCompat.getColor(context, R.color.black)
                    )
            }
    }

}