package com.agnesai.chat.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内部媒体缓存工具：生成结果自动下载到 App 私有目录，离线也能播放。
 */

/** 由 URL 确定对应的本地缓存文件（不触发下载）。 */
fun localMediaFileForUrl(context: Context, url: String): File {
    val dir = File(context.filesDir, "media").apply { mkdirs() }
    val name = "video_${abs(url.hashCode())}.mp4"
    return File(dir, name)
}

/** 返回已缓存的本地视频文件；URL 本身就是本地路径时原样返回。不存在则返回 null。 */
fun cachedLocalVideo(context: Context, url: String): File? {
    if (!url.startsWith("http")) return File(url).takeIf { it.exists() }
    val f = localMediaFileForUrl(context, url)
    return f.takeIf { it.exists() && it.length() > 0L }
}

/** 已缓存则返回本地路径，否则返回原始 URL。 */
fun videoPlaySource(context: Context, url: String): String =
    cachedLocalVideo(context, url)?.absolutePath ?: url

/**
 * 返回视频首帧封面文件；本地无缓存视频时返回 null，否则从缓存提取并缓存 JPEG。
 *
 * @return 已存在或成功提取的封面文件，失败返回 null
 */
suspend fun videoThumbnailFile(context: Context, url: String): File? =
    withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext null
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val target = File(dir, "thumb_${abs(url.hashCode())}.jpg")
        if (target.exists() && target.length() > 0L) return@withContext target
        val source = cachedLocalVideo(context, url)?.absolutePath ?: return@withContext null
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(source)
                val frame =
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                if (frame == null) return@withContext null
                val tmp = File(dir, target.name + ".tmp")
                FileOutputStream(tmp).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (tmp.length() > 0L) {
                    if (target.exists()) target.delete()
                    tmp.renameTo(target)
                    target
                } else {
                    tmp.delete()
                    null
                }
            } finally {
                runCatching { retriever.release() }
            }
        } catch (e: Exception) {
            null
        }
    }

/**
 * 将视频 URL 自动下载到应用内部存储（已缓存时直接跳过）。
 *
 * @return true 表示本地已有可播放文件（下载成功或原本就是本地路径）
 */
suspend fun downloadVideoToInternalStorage(context: Context, url: String): Boolean =
    withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext true
        val target = localMediaFileForUrl(context, url)
        if (target.exists() && target.length() > 0L) return@withContext true
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "AgnesAI/1.0")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return@withContext false
            val tmp = File(target.parentFile, target.name + ".tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() > 0L) {
                if (target.exists()) target.delete()
                tmp.renameTo(target)
                true
            } else {
                tmp.delete()
                false
            }
        } catch (e: Exception) {
            false
        }
    }
