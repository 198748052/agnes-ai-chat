package com.agnesai.chat.ui.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下载图片 URL 并保存到系统相册（Android 10+ 无需存储权限）。
 *
 * @return true 表示保存成功
 */
fun saveImageToGallery(context: Context, url: String): Boolean {
    return try {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val fileName = "agnes_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AgnesAI")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "AgnesAI"
                )
                if (!dir.exists()) dir.mkdirs()
                put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { resolver.openOutputStream(it)?.use { os -> os.write(bytes) } } != null
    } catch (e: Exception) {
        false
    }
}

/** 在后台线程保存图片并弹出结果提示。 */
fun saveImageWithToast(context: Context, scope: CoroutineScope, url: String) {
    scope.launch(Dispatchers.IO) {
        val ok = saveImageToGallery(context, url)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                if (ok) "图片已保存到相册" else "保存失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/** 用系统播放器打开视频 URL。 */
fun openVideoWithSystemPlayer(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(android.net.Uri.parse(url), "video/mp4")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "没有可用的视频播放器", Toast.LENGTH_SHORT).show()
        }
    }
}

/** 分享图片/视频资源 URL。 */
fun shareMediaUrl(context: Context, url: String, type: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, if (type.startsWith("image")) "分享图片" else "分享视频"))
    }.onFailure {
        Toast.makeText(context, "无法打开分享面板", Toast.LENGTH_SHORT).show()
    }
}

/** 格式化时间戳为 yyyy-MM-dd HH:mm。 */
fun formatTimestamp(timestamp: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    val y = cal.get(Calendar.YEAR)
    val mo = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val mi = cal.get(Calendar.MINUTE)
    return "%04d-%02d-%02d %02d:%02d".format(y, mo, d, h, mi)
}
