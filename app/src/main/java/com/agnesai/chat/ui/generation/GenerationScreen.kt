package com.agnesai.chat.ui.generation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agnesai.chat.data.generation.GenerationParamsCodec
import com.agnesai.chat.data.local.Roles
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.IMAGE_MODEL_2_0
import com.agnesai.chat.data.network.IMAGE_MODEL_2_1
import com.agnesai.chat.ui.chat.UiMessage
import com.agnesai.chat.ui.common.VideoPlayerDialog
import com.agnesai.chat.ui.common.downloadVideoToInternalStorage
import com.agnesai.chat.ui.common.videoPlaySource
import com.agnesai.chat.ui.common.videoThumbnailFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "GenerationScreen"

/**
 * 图片生成面板（对话式）：消息流 + 输入框上方参数选择区 + 底部输入栏。
 */
@Composable
fun ImageGenerationPanel(
    viewModel: GenerationViewModel,
    messages: List<UiMessage>,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.image
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 仅当生成任务属于当前会话时才展示加载态/禁用输入，其他会话后台生成不阻塞本会话
    val generatingHere = state.isGenerating && sessionId in state.generatingSessionIds

    // 当前正在应用内播放的视频 URL（非空时显示内置播放器弹窗）
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    val pickReferenceImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        scope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                uriToDataUri(context, uri)?.let {
                    withContext(Dispatchers.Main) { viewModel.addReferenceImage(it) }
                }
            }
        }
    }

    // API 26-28 保存图片需要存储权限
    val requestStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            state.result?.let { saveResultImage(context, scope, it) }
        } else {
            Toast.makeText(context, "需要存储权限才能保存图片，请在系统设置中开启", Toast.LENGTH_SHORT).show()
        }
    }

    fun onSaveClick(url: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveResultImage(context, scope, url)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && !generatingHere) {
                item { GenerationWelcomeHint("输入提示词，AI 将为你生成图片") }
            }
            items(messages, key = { it.id }) { message ->
                GenerationMessageItem(
                    message = message,
                    onSaveImage = { url -> onSaveClick(url) },
                    onShareImage = { url -> shareImage(context, url) },
                    onRegenerate = { viewModel.regenerateImage(sessionId) },
                    onOpenVideo = { url -> playingVideoUrl = url }
                )
            }
            if (generatingHere) {
                item(key = "generating") {
                    GeneratingBubble("AI 生成图片中...")
                }
            }
        }

        ImageParamsBar(
            state = state,
            enabled = !generatingHere,
            onAddReference = {
                pickReferenceImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveReference = viewModel::removeReferenceImage,
            onSelectModel = viewModel::setImageModel,
            onSelectRatio = viewModel::setImageRatio
        )

        GenerationInputBar(
            value = state.prompt,
            onValueChange = viewModel::updateImagePrompt,
            placeholder = "描述你想生成的图片，例如：赛博朋克风格的机械狐狸",
            enabled = !generatingHere,
            onSend = {
                if (state.prompt.isBlank()) {
                    Toast.makeText(context, "请输入提示词描述", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.sendImagePrompt(sessionId)
                }
            }
        )
    }

    playingVideoUrl?.let { url ->
        VideoPlayerDialog(
            url = videoPlaySource(context, url),
            title = "视频播放",
            onDismiss = { playingVideoUrl = null }
        )
    }
}

/**
 * 视频生成面板（对话式）：消息流 + 输入框上方参数选择区 + 底部输入栏。
 */
@Composable
fun VideoGenerationPanel(
    viewModel: GenerationViewModel,
    messages: List<UiMessage>,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.video
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 仅当生成任务属于当前会话时才展示加载态/禁用输入，其他会话后台生成不阻塞本会话
    val generatingHere = state.isGenerating && sessionId in state.generatingSessionIds

    // 当前正在应用内播放的视频 URL（非空时显示内置播放器弹窗）
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    // 生成结果自动下载到应用内部存储，离线也能播放
    val autoSavedKeys = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(messages) {
        messages.forEach { m ->
            val params = GenerationParamsCodec.decode(m.params)
            if (params?.type == SessionType.VIDEO && m.content.startsWith("http") &&
                m.content !in autoSavedKeys.value
            ) {
                autoSavedKeys.value = autoSavedKeys.value + m.content
                downloadVideoToInternalStorage(context, m.content)
            }
        }
    }

    // 首帧 / 尾帧选择共用同一个 launcher，通过目标槽位区分
    var frameTarget by remember { mutableStateOf(FrameTarget.FIRST) }
    val pickFrame = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        scope.launch(Dispatchers.IO) {
            val data = uri?.let { uriToDataUri(context, it) }
            withContext(Dispatchers.Main) {
                when (frameTarget) {
                    FrameTarget.FIRST -> viewModel.setFirstFrameImage(data)
                    FrameTarget.LAST -> viewModel.setLastFrameImage(data)
                }
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && !generatingHere) {
                item { GenerationWelcomeHint("输入提示词，AI 将为你生成视频") }
            }
            items(messages, key = { it.id }) { message ->
                GenerationMessageItem(
                    message = message,
                    onSaveImage = { _ -> Toast.makeText(context, "仅图片支持保存", Toast.LENGTH_SHORT).show() },
                    onShareImage = { url -> shareImage(context, url) },
                    onRegenerate = { viewModel.regenerateVideo(sessionId) },
                    onOpenVideo = { url -> playingVideoUrl = url }
                )
            }
            if (generatingHere) {
                item(key = "generating") {
                    GeneratingBubble("AI 生成视频中...")
                }
            }
        }

        VideoParamsBar(
            state = state,
            enabled = !generatingHere,
            onPickFirstFrame = {
                frameTarget = FrameTarget.FIRST
                pickFrame.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickLastFrame = {
                frameTarget = FrameTarget.LAST
                pickFrame.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveFirstFrame = { viewModel.setFirstFrameImage(null) },
            onRemoveLastFrame = { viewModel.setLastFrameImage(null) },
            onSelectDuration = viewModel::setVideoDuration,
            onSelectQuality = viewModel::setVideoQuality,
            onSelectRatio = viewModel::setVideoRatio
        )

        GenerationInputBar(
            value = state.prompt,
            onValueChange = viewModel::updateVideoPrompt,
            placeholder = "描述视频内容、动作、场景，例如：镜头缓缓推进，阳光透过树叶...",
            enabled = !generatingHere,
            onSend = {
                if (state.prompt.isBlank()) {
                    Toast.makeText(context, "请输入提示词描述", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.sendVideoPrompt(sessionId)
                }
            }
        )
    }

    playingVideoUrl?.let { url ->
        VideoPlayerDialog(
            url = videoPlaySource(context, url),
            title = "视频播放",
            onDismiss = { playingVideoUrl = null }
        )
    }
}

private enum class FrameTarget { FIRST, LAST }

// ========== 消息流 ==========

@Composable
private fun GenerationMessageItem(
    message: UiMessage,
    onSaveImage: (String) -> Unit,
    onShareImage: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    val params = GenerationParamsCodec.decode(message.params)
    when {
        message.role == Roles.USER -> UserBubble(message.content)
        message.isError -> ErrorBubble(message.content)
        params?.type == SessionType.IMAGE -> ImageResultCard(
            result = message.content,
            onSave = { onSaveImage(message.content) },
            onShare = { onShareImage(message.content) },
            onRegenerate = onRegenerate
        )
        params?.type == SessionType.VIDEO -> VideoResultCard(
            result = message.content,
            onOpen = { onOpenVideo(message.content) }
        )
        else -> AssistantBubble(message.content)
    }
}

@Composable
private fun UserBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantBubble(content: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = content.ifBlank { "..." },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ErrorBubble(content: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun GeneratingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GenerationWelcomeHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✨",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ========== 参数选择区 ==========

@Composable
private fun ImageParamsBar(
    state: ImageGenState,
    enabled: Boolean,
    onAddReference: () -> Unit,
    onRemoveReference: (Int) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectRatio: (String) -> Unit
) {
    var referenceExpanded by remember { mutableStateOf(state.referenceImages.isNotEmpty()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CollapsibleParamChip(
                label = "比例",
                valueLabel = state.ratio,
                options = listOf("1:1" to "1:1", "16:9" to "16:9", "9:16" to "9:16"),
                selected = state.ratio,
                enabled = enabled,
                onSelect = onSelectRatio
            )
            CollapsibleParamChip(
                label = "模型",
                valueLabel = if (state.imageModel == IMAGE_MODEL_2_1) "2.1 Flash" else "2.0 Flash",
                options = listOf(
                    IMAGE_MODEL_2_1 to IMAGE_MODEL_2_1,
                    IMAGE_MODEL_2_0 to IMAGE_MODEL_2_0
                ),
                selected = state.imageModel,
                enabled = enabled,
                onSelect = onSelectModel
            )
            ReferenceChip(
                images = state.referenceImages,
                enabled = enabled,
                onAdd = onAddReference,
                onToggle = { referenceExpanded = !referenceExpanded }
            )
        }
        AnimatedVisibility(visible = referenceExpanded) {
            ReferenceImageGrid(
                images = state.referenceImages,
                enabled = enabled,
                onAdd = onAddReference,
                onRemove = onRemoveReference
            )
        }
    }
}

@Composable
private fun VideoParamsBar(
    state: VideoGenState,
    enabled: Boolean,
    onPickFirstFrame: () -> Unit,
    onPickLastFrame: () -> Unit,
    onRemoveFirstFrame: () -> Unit,
    onRemoveLastFrame: () -> Unit,
    onSelectDuration: (String) -> Unit,
    onSelectQuality: (String) -> Unit,
    onSelectRatio: (String) -> Unit
) {
    var framesExpanded by remember {
        mutableStateOf(state.firstFrameImage != null || state.lastFrameImage != null)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FramesChip(
                firstFrame = state.firstFrameImage,
                lastFrame = state.lastFrameImage,
                enabled = enabled,
                onToggle = { framesExpanded = !framesExpanded }
            )
            CollapsibleParamChip(
                label = "时长",
                valueLabel = if (state.duration == "5s") "5 秒" else "10 秒",
                options = listOf("5s" to "5s", "10s" to "10s"),
                selected = state.duration,
                enabled = enabled,
                onSelect = onSelectDuration
            )
            CollapsibleParamChip(
                label = "清晰度",
                valueLabel = state.quality,
                options = listOf("720P" to "720P", "1080P" to "1080P"),
                selected = state.quality,
                enabled = enabled,
                onSelect = onSelectQuality
            )
            CollapsibleParamChip(
                label = "比例",
                valueLabel = state.ratio,
                options = listOf("16:9" to "16:9", "9:16" to "9:16", "1:1" to "1:1"),
                selected = state.ratio,
                enabled = enabled,
                onSelect = onSelectRatio
            )
        }
        AnimatedVisibility(visible = framesExpanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameUploadBox(
                    label = "上传首帧",
                    uploadedLabel = "首帧已上传",
                    imageUri = state.firstFrameImage,
                    enabled = enabled,
                    onPick = onPickFirstFrame,
                    onRemove = onRemoveFirstFrame,
                    modifier = Modifier.weight(1f)
                )
                FrameUploadBox(
                    label = "上传尾帧",
                    uploadedLabel = "尾帧已上传",
                    imageUri = state.lastFrameImage,
                    enabled = enabled,
                    onPick = onPickLastFrame,
                    onRemove = onRemoveLastFrame,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ========== 底部输入栏 ==========

@Composable
private fun GenerationInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    onSend: () -> Unit
) {
    // 底部导航栏由外层 Scaffold 顶起，输入栏只响应 IME 弹起的额外高度
    val density = LocalDensity.current
    val navBarHeight = with(density) { 80.dp.toPx() } + WindowInsets.navigationBars.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomInset = with(density) { (imeBottom - navBarHeight).coerceAtLeast(0f).toDp() }

    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomInset)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                enabled = enabled,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (value.isNotBlank() && enabled) onSend()
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

// ========== 通用组件 ==========

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

/** 折叠参数胶囊块：平时只显示"标签 + 当前值"，点击展开下拉选项，选择后自动收起。 */
@Composable
private fun <T> CollapsibleParamChip(
    label: String,
    valueLabel: String,
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ParamChip(
            text = "$label $valueLabel",
            onClick = { if (enabled) expanded = true },
            enabled = enabled
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, _) ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onSelect(value as T)
                        expanded = false
                    },
                    leadingIcon = {
                        if (value == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ParamChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 参考图胶囊块：无图时点击直接选图，有图时点击展开缩略图网格管理。 */
@Composable
private fun ReferenceChip(
    images: List<String>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        onClick = {
            if (enabled) {
                if (images.isNotEmpty()) onToggle() else onAdd()
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (images.isNotEmpty()) {
                AsyncImage(
                    model = images.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (images.isNotEmpty()) "参考图 ${images.size}" else "参考图",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 视频首尾帧胶囊块：有帧显示缩略图，点击展开首帧/尾帧上传管理面板。 */
@Composable
private fun FramesChip(
    firstFrame: String?,
    lastFrame: String?,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val preview = firstFrame ?: lastFrame
    Surface(
        onClick = { if (enabled) onToggle() },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (preview != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = "首尾帧",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ========== 参考图网格 ==========

@Composable
private fun ReferenceImageGrid(
    images: List<String>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    val showAdd = images.size < 6 && enabled
    val items: List<String?> = images + if (showAdd) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { item ->
                    if (item == null) {
                        AddImageItem(
                            onClick = onAdd,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ReferenceImageItem(
                            imageUri = item,
                            enabled = enabled,
                            onRemove = { onRemove(images.indexOf(item)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(3 - chunk.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReferenceImageItem(
    imageUri: String,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "参考图",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x99000000)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移除",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun AddImageItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加参考图",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ========== 视频首帧 / 尾帧 ==========

@Composable
private fun FrameUploadBox(
    label: String,
    uploadedLabel: String,
    imageUri: String?,
    enabled: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageUri != null) {
        Box(
            modifier = modifier
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = uploadedLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Text(
                text = uploadedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x99000000)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(2.dp)
                    )
                }
            }
        }
    } else {
        Surface(
            onClick = onPick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier.aspectRatio(16f / 9f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ========== 结果卡片 ==========

@Composable
private fun ImageResultCard(
    result: String,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    // 加载失败后允许用户点击重试：更换 memoryCacheKey 强制 Coil 重新发起加载
    var retryKey by remember(result) { mutableStateOf(0) }
    var failed by remember(result) { mutableStateOf(false) }
    val context = LocalContext.current
    val imageRequest = remember(result, retryKey) {
        ImageRequest.Builder(context)
            .data(result)
            .memoryCacheKey("$result-$retryKey")
            .build()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "生成的图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { state ->
                        failed = true
                        Log.w(TAG, "图片加载失败: $result", state.result.throwable)
                    },
                    onSuccess = { failed = false }
                )
                if (failed) {
                    Surface(
                        onClick = {
                            failed = false
                            retryKey++
                        },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "图片加载失败，点击重试",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultActionButton("保存", onSave, Modifier.weight(1f))
            ResultActionButton("分享", onShare, Modifier.weight(1f))
            ResultActionButton("再次生成", onRegenerate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VideoResultCard(
    result: String,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<File?>(initialValue = null, result) {
        value = videoThumbnailFile(context, result)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        val thumb = thumbnail
        if (thumb != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = "视频封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    onClick = { onOpen(result) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    onClick = { onOpen(result) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

// ========== 工具函数 ==========

/** 把本地图片 content Uri 读取并编码为 Data URI Base64（Agnes API 需要公网 URL 或 Data URI）。 */
private fun uriToDataUri(context: Context, uri: Uri): String? {
    return try {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

/** 下载图片 URL 并保存到系统相册（Android 10+ 无需存储权限）。 */
private fun saveImageToGallery(context: Context, url: String): Boolean {
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
private fun saveResultImage(context: Context, scope: CoroutineScope, url: String) {
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

/** 分享图片 URL。 */
private fun shareImage(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "分享图片")) }
}
