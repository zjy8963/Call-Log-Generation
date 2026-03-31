package com.uselesswater.multicallloggeneration.analyze

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
/**
 * 抖音内容下载管理器
 */
class DouyinDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // 下载状态
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private var currentDownloadId: Long = -1
    // 标记广播是否已注册，防止重复注册
    private var isReceiverRegistered = false

    /**
     * 下载任务数据类
     */
    data class DownloadTask(
        val url: String,
        val fileName: String,
        val subPath: String,
        val mimeType: String
    )

    /**
     * 下载状态密封类
     */
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int, val fileName: String) : DownloadState()
        data class Completed(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    /**
     * 下载视频
     */
    fun downloadVideo(videoData: DouyinVideoData, useBackup: Boolean = false) {
        val videoUrl = if (useBackup && !videoData.videoBackup.isNullOrEmpty()) {
            videoData.videoBackup.first()
        } else {
            videoData.url
        } ?: return

        val safeTitle = sanitizeFileName(videoData.title).take(50)
        // 修复：删除多余的下划线 safeTitle_ → safeTitle
        val fileName = "douyin_video_${System.currentTimeMillis()}_$safeTitle.mp4"

        enqueueDownload(videoUrl, fileName, "Douyin/Videos", "video/mp4")
    }

    /**
     * 下载图片
     */
    fun downloadImage(imageUrl: String, index: Int, title: String) {
        val extension = getFileExtension(imageUrl)
        val safeTitle = sanitizeFileName(title).take(30)
        val fileName = "douyin_img_${index + 1}_${System.currentTimeMillis()}_$safeTitle.$extension"

        enqueueDownload(imageUrl, fileName, "Douyin/Images", "image/*")
    }

    /**
     * 下载实况照片（Live Photo）
     */
    fun downloadLivePhoto(livePhoto: LivePhoto, index: Int, title: String) {
        // 下载图片
        val imgExtension = getFileExtension(livePhoto.image)
        val safeTitle = sanitizeFileName(title).take(30)
        val imgFileName = "douyin_live_${index + 1}_img_${System.currentTimeMillis()}_$safeTitle.$imgExtension"
        enqueueDownload(livePhoto.image, imgFileName, "Douyin/LivePhotos/Images", "image/*")

        // 下载视频
        val videoFileName = "douyin_live_${index + 1}_video_${System.currentTimeMillis()}_$safeTitle.mp4"
        enqueueDownload(livePhoto.video, videoFileName, "Douyin/LivePhotos/Videos", "video/mp4")
    }

    /**
     * 下载音乐
     */
    fun downloadMusic(musicUrl: String, title: String, author: String?) {
        val safeTitle = sanitizeFileName(title).take(40)
        val safeAuthor = author?.let { sanitizeFileName(it).take(20) } ?: "unknown"
        val fileName = "douyin_music_${safeAuthor}_${safeTitle}_${System.currentTimeMillis()}.mp3"

        enqueueDownload(musicUrl, fileName, "Douyin/Music", "audio/mpeg")
    }

    /**
     * 批量下载所有图片
     */
    fun downloadAllImages(images: List<String>, title: String) {
        images.forEachIndexed { index, url ->
            downloadImage(url, index, title)
        }
        Toast.makeText(context, "已添加 ${images.size} 张图片到下载队列", Toast.LENGTH_SHORT).show()
    }

    /**
     * 批量下载所有实况照片
     */
    fun downloadAllLivePhotos(livePhotos: List<LivePhoto>, title: String) {
        livePhotos.forEachIndexed { index, livePhoto ->
            downloadLivePhoto(livePhoto, index, title)
        }
        Toast.makeText(context, "已添加 ${livePhotos.size} 组实况照片到下载队列", Toast.LENGTH_SHORT).show()
    }

    /**
     * 添加下载任务到队列
     */
    private fun enqueueDownload(url: String, fileName: String, subPath: String, mimeType: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("正在下载...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$subPath/$fileName")
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            addRequestHeader("User-Agent", "Mozilla/5.0")
        }

        // 先反注册旧的广播，防止重复
        unregisterDownloadReceiver()

        val downloadId = downloadManager.enqueue(request)
        currentDownloadId = downloadId

        // 注册下载完成广播（修复：添加RECEIVER_EXPORTED标志）
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                downloadCompleteReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        isReceiverRegistered = true

        _downloadState.value = DownloadState.Downloading(0, fileName)
        Toast.makeText(context, "开始下载: $fileName", Toast.LENGTH_SHORT).show()
    }

    /**
     * 下载完成广播接收器（修复：自动反注册，避免内存泄漏）
     */
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == currentDownloadId) {
                _downloadState.value = DownloadState.Idle
                // 自动反注册
                unregisterDownloadReceiver()
            }
        }
    }

    /**
     * 安全反注册广播
     */
    private fun unregisterDownloadReceiver() {
        if (isReceiverRegistered) {
            context.unregisterReceiver(downloadCompleteReceiver)
            isReceiverRegistered = false
        }
    }

    /**
     * 打开下载的文件
     */
    fun openDownloadedFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }

    /**
     * 清理文件名中的非法字符
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), "_")
            .trim()
    }

    /**
     * 从URL获取文件扩展名
     */
    private fun getFileExtension(url: String): String {
        return try {
            val path = Uri.parse(url).path ?: return "jpg"
            MimeTypeMap.getFileExtensionFromUrl(path) ?: "jpg"
        } catch (e: Exception) {
            "jpg"
        }
    }

    /**
     * 获取文件的MIME类型
     */
    private fun getMimeType(file: File): String {
        val extension = file.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    /**
     * 页面销毁时调用，防止内存泄漏
     */
    fun onDestroy() {
        unregisterDownloadReceiver()
    }
}