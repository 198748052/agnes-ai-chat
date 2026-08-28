package com.agnesai.chat.ui.myworks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agnesai.chat.data.generation.GenerationParams
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.works.MyWork
import com.agnesai.chat.ui.common.FullScreenVideoPlayer
import com.agnesai.chat.ui.common.formatTimestamp
import com.agnesai.chat.ui.common.saveImageWithToast
import com.agnesai.chat.ui.common.shareMediaUrl
import com.agnesai.chat.ui.common.videoPlaySource
import kotlinx.coroutines.CoroutineScope

@Composable
fun MyWorksDetail(
    work: MyWork,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onOpenConversation: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2000000))
        ) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var retryKey by remember(work.id) { mutableStateOf(0) }
            var failed by remember(work.id) { mutableStateOf(false) }
            // 是否在应用内全屏播放视频
            var playingVideo by remember(work.id) { mutableStateOf(false) }

            val imageRequest = remember(work.id, retryKey) {
                ImageRequest.Builder(context)
                    .data(work.url)
                    .memoryCacheKey("${work.url}-$retryKey")
                    .build()
            }

            Column(modifier = Modifier.fillMaxSize()) {
                DetailTopBar(
                    work = work,
                    onDismiss = onDismiss,
                    onOpenConversation = onOpenConversation
                )

                // 预览区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (work.type == SessionType.VIDEO) {
                        PlayPreview(
                            onPlay = { playingVideo = true }
                        )
                    } else {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "作品大图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onError = { failed = true },
                            onSuccess = { failed = false }
                        )
                        if (failed) {
                            RetryPreview(
                                onClick = {
                                    failed = false
                                    retryKey++
                                }
                            )
                        }
                    }
                }

                // 信息区
                DetailInfo(work)

                // 操作区
                DetailActions(
                    isVideo = work.type == SessionType.VIDEO,
                    context = context,
                    scope = scope,
                    work = work,
                    onDelete = onDelete
                )
            }

            if (playingVideo) {
                FullScreenVideoPlayer(
                    url = videoPlaySource(context, work.url),
                    title = work.sessionTitle,
                    onClose = { playingVideo = false }
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    work: MyWork,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
        }
        Text(
            text = work.sessionTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenConversation) {
            Icon(
                Icons.Filled.SyncAlt,
                contentDescription = "查看原对话",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PlayPreview(onPlay: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onPlay,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放视频",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "点击播放视频",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
private fun RetryPreview(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0x99000000),
        shape = RoundedCornerShape(12.dp),
        contentColor = Color.White
    ) {
        Text(
            text = "图片加载失败，点击重试",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun DetailInfo(work: MyWork) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x66000000))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (work.type == SessionType.VIDEO) "视频作品" else "图片作品",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatTimestamp(work.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xCCFFFFFF)
            )
        }
        work.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        work.params?.let { params ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = describeParams(params),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xBBFFFFFF)
            )
        }
    }
}

@Composable
private fun DetailActions(
    isVideo: Boolean,
    context: Context,
    scope: CoroutineScope,
    work: MyWork,
    onDelete: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (!isVideo) {
            ActionButton(
                text = "保存",
                icon = Icons.Filled.SaveAlt,
                modifier = Modifier.weight(1f)
            ) {
                saveImageWithToast(context, scope, work.url)
            }
        }
        ActionButton(
            text = "分享",
            icon = Icons.Filled.Share,
            modifier = Modifier.weight(1f)
        ) {
            shareMediaUrl(context, work.url, if (isVideo) "video/*" else "image/*")
        }
        ActionButton(
            text = "删除",
            icon = Icons.Filled.Delete,
            danger = true,
            modifier = Modifier.weight(1f),
            onClick = onDelete
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = if (danger) MaterialTheme.colorScheme.error else Color.White
        )
    }
}

private fun describeParams(params: GenerationParams): String {
    val parts = mutableListOf<String>()
    params.model?.takeIf { it.isNotBlank() }?.let { parts.add("模型 $it") }
    params.ratio?.takeIf { it.isNotBlank() }?.let { parts.add("比例 $it") }
    params.duration?.takeIf { it.isNotBlank() }?.let { parts.add("时长 $it") }
    params.quality?.takeIf { it.isNotBlank() }?.let { parts.add("清晰度 $it") }
    if (params.referenceImages.isNotEmpty()) parts.add("参考图 ${params.referenceImages.size} 张")
    if (params.firstFrameImage != null) parts.add("首帧")
    if (params.lastFrameImage != null) parts.add("尾帧")
    return parts.joinToString(" · ")
}
