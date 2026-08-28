package com.agnesai.chat.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.LinkedHashMap

/** 单条用户消息可附带的最大图片数量。 */
const val MAX_MESSAGE_IMAGES = 6

/** 单张图片大小上限，超过则拒绝添加。 */
const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** 图片压缩后最长边像素，控制请求体大小。 */
const val IMAGE_MAX_EDGE_PX = 1024

/** 压缩 JPEG 质量。 */
const val IMAGE_JPEG_QUALITY = 80

/** 图片相对路径的基础目录名（位于 filesDir 下）。 */
const val MESSAGE_IMAGES_DIR = "message_images"

/** Data URI 内存缓存最大条目数：超出按 LRU 淘汰，限制常驻内存。 */
const val MAX_CACHED_DATA_URIS = 64

/** 把图片相对路径列表编码为 JSON 数组字符串（路径由程序生成，JSON 安全）。 */
fun encodeImagePaths(paths: List<String>): String =
    "[" + paths.joinToString(",") { "\"" + it + "\"" } + "]"

/** 解析消息的图片相对路径 JSON 数组字符串；非法或空返回空列表。 */
fun parseImagePaths(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    val trimmed = json.trim()
    if (trimmed.length < 2 || !trimmed.startsWith("[") || !trimmed.endsWith("]")) {
        return emptyList()
    }
    if (trimmed == "[]") return emptyList()
    return runCatching {
        trimmed
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())
}

/** 把原始字节编码为 Data URI，供 OpenAI 兼容 image_url 使用。 */
fun bytesToDataUri(mime: String, bytes: ByteArray): String =
    "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

/**
 * 图片持久化结果：成功时 [relativePaths] / [dataUris] 与输入一一对应；
 * 任一图片失败则 [error] 非空（已写入的文件会被清理，不保留半成品）。
 */
data class PersistImagesResult(
    val relativePaths: List<String> = emptyList(),
    val dataUris: List<String> = emptyList(),
    val error: String? = null
)

/**
 * 消息图片存储：把选中的图片复制到 App 内部存储并压缩，随消息持久化；
 * 发送时再读回编码 Data URI，避免相册原图被删除后失效。
 */
interface MessageImageStore {
    suspend fun persistImages(sessionId: Long, uris: List<Uri>): PersistImagesResult
    suspend fun loadDataUri(relativePath: String): String?
    suspend fun deleteMessageImages(sessionId: Long, relativePaths: List<String>)
    suspend fun deleteSessionImages(sessionId: Long)
}

class MessageImageStoreImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MessageImageStore {

    /** Data URI 内存缓存：历史图片同一轮对话内多次请求会重复读盘 + Base64，这里按 LRU 缓存最近使用的结果。 */
    private val dataUriCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHED_DATA_URIS
    }

    override suspend fun persistImages(sessionId: Long, uris: List<Uri>): PersistImagesResult =
        withContext(ioDispatcher) {
            val dir = messageImagesDir(sessionId)
            dir.mkdirs()
            val relativePaths = mutableListOf<String>()
            val dataUris = mutableListOf<String>()
            val baseTime = System.currentTimeMillis()
            try {
                uris.forEachIndexed { index, uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw ImageStoreException("图片读取失败")
                    if (bytes.size > MAX_IMAGE_BYTES) {
                        throw ImageStoreException("图片超过 5MB，请压缩后重试")
                    }
                    val compressed = compressBytes(bytes)
                    val fileName = "msg_${baseTime}_$index.jpg"
                    File(dir, fileName).writeBytes(compressed)
                    val relPath = "$MESSAGE_IMAGES_DIR/${sessionId}/$fileName"
                    relativePaths += relPath
                    val dataUri = bytesToDataUri("image/jpeg", compressed)
                    dataUris += dataUri
                    dataUriCache[relPath] = dataUri
                }
                PersistImagesResult(relativePaths, dataUris)
            } catch (e: Exception) {
                // 清理已写入的半成品
                relativePaths.forEach { File(context.filesDir, it).delete() }
                val message = if (e is ImageStoreException) {
                    e.message ?: "图片处理失败"
                } else {
                    "图片读取失败"
                }
                PersistImagesResult(error = message)
            }
        }

    override suspend fun loadDataUri(relativePath: String): String? = withContext(ioDispatcher) {
        dataUriCache[relativePath]?.let { return@withContext it }
        val file = File(context.filesDir, relativePath)
        if (!file.exists() || file.length() == 0L) return@withContext null
        val dataUri = runCatching {
            bytesToDataUri("image/jpeg", file.readBytes())
        }.getOrNull()
        if (dataUri != null) dataUriCache[relativePath] = dataUri
        dataUri
    }

    override suspend fun deleteMessageImages(sessionId: Long, relativePaths: List<String>) {
        withContext(ioDispatcher) {
            relativePaths.forEach { path ->
                dataUriCache.remove(path)
                File(context.filesDir, path).delete()
            }
        }
    }

    override suspend fun deleteSessionImages(sessionId: Long) {
        withContext(ioDispatcher) {
            val prefix = "$MESSAGE_IMAGES_DIR/$sessionId/"
            dataUriCache.keys.retainAll { it?.startsWith(prefix) != true }
            messageImagesDir(sessionId).deleteRecursively()
        }
    }

    private fun messageImagesDir(sessionId: Long): File =
        File(context.filesDir, "$MESSAGE_IMAGES_DIR/$sessionId")

    /** 解码并压缩：最长边缩放到 [IMAGE_MAX_EDGE_PX]，JPEG 质量 [IMAGE_JPEG_QUALITY]。 */
    private fun compressBytes(bytes: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) throw ImageStoreException("图片读取失败")

        var scale = 1
        while (maxOf(width / scale, height / scale) > IMAGE_MAX_EDGE_PX) scale *= 2

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw ImageStoreException("图片读取失败")

        val target = if (maxOf(decoded.width, decoded.height) > IMAGE_MAX_EDGE_PX) {
            val ratio = IMAGE_MAX_EDGE_PX.toFloat() / maxOf(decoded.width, decoded.height)
            val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        try {
            target.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
            return out.toByteArray()
        } finally {
            target.recycle()
        }
    }

    private class ImageStoreException(message: String) : Exception(message)
}
