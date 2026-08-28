package com.agnesai.chat.ui.common

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 内置视频播放器：应用内弹窗全屏播放，不跳转系统播放器。
 * 支持网络 URL 与本地文件路径（file:// 或绝对路径）。
 */
@Composable
fun VideoPlayerDialog(
    url: String,
    title: String? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FullScreenVideoPlayer(url = url, title = title, onClose = onDismiss)
    }
}

/**
 * 全屏视频播放视图：可放在 Dialog 中，也可作为页面覆盖层使用。
 */
@Composable
fun FullScreenVideoPlayer(
    url: String,
    title: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }
    var loadFailed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { videoView.stopPlayback() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
                Text(
                    text = title ?: "视频播放",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView<VideoView>(
                    factory = { context ->
                        val vv = VideoView(context)
                        val controller = MediaController(context).apply { setAnchorView(vv) }
                        vv.setMediaController(controller)
                        vv.setOnPreparedListener { vv.start() }
                        vv.setOnErrorListener { _, _, _ ->
                            loadFailed = true
                            true
                        }
                        vv.setVideoURI(Uri.parse(url))
                        vv
                    }
                )
                if (loadFailed) {
                    Text(
                        text = "视频加载失败",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}
