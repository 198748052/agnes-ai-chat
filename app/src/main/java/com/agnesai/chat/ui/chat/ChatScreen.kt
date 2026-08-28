package com.agnesai.chat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.repository.MAX_MESSAGE_IMAGES
import com.agnesai.chat.ui.conversation.ConversationDrawerContent
import com.agnesai.chat.ui.generation.GenerationViewModel
import com.agnesai.chat.ui.generation.ImageGenerationPanel
import com.agnesai.chat.ui.generation.VideoGenerationPanel
import kotlinx.coroutines.launch
import java.io.File

/** 顶部功能切换：文本聊天 / 图片生成 / 视频生成 */
enum class FeatureTab(val label: String, val sessionType: String) {
    CHAT("文本聊天", SessionType.CHAT),
    IMAGE("图片生成", SessionType.IMAGE),
    VIDEO("视频生成", SessionType.VIDEO)
}

/** 抽屉标题随能力变化 */
private val FeatureTab.drawerTitle: String
    get() = when (this) {
        FeatureTab.CHAT -> "文本聊天记录"
        FeatureTab.IMAGE -> "图片生成记录"
        FeatureTab.VIDEO -> "视频生成记录"
    }

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    generationViewModel: GenerationViewModel,
    openSessionId: Long? = null,
    openType: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val streamingContent by viewModel.streamingContent.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var input by remember { mutableStateOf("") }
    var currentFeature by rememberSaveable { mutableStateOf(FeatureTab.CHAT) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // ===== 多模态图片选择 =====
    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun addPickedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val sessionId = uiState.currentSessionId
        if (sessionId == 0L) {
            Toast.makeText(context, "当前会话不可用", Toast.LENGTH_SHORT).show()
            return
        }
        val remain = MAX_MESSAGE_IMAGES - selectedImages.size
        scope.launch {
            val result = viewModel.persistMessageImages(sessionId, uris.take(remain))
            if (result.error != null) {
                Toast.makeText(context, result.error, Toast.LENGTH_SHORT).show()
            } else {
                selectedImages = (selectedImages + result.relativePaths).take(MAX_MESSAGE_IMAGES)
            }
        }
    }

    fun onAddImageClick() {
        if (selectedImages.size >= MAX_MESSAGE_IMAGES) {
            Toast.makeText(context, "最多选择 $MAX_MESSAGE_IMAGES 张图片", Toast.LENGTH_SHORT).show()
            return
        }
        showImageSourceDialog = true
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_MESSAGE_IMAGES)
    ) { uris -> addPickedImages(uris) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        cameraUri = null
        if (success && uri != null) addPickedImages(listOf(uri))
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        cameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, "相机权限被拒绝，无法拍照", Toast.LENGTH_SHORT).show()
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("添加图片") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            pickImagesLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) { Text("从相册选择（可多选）") }
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                launchCamera()
                            } else {
                                requestCameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ) { Text("拍照") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) { Text("取消") }
            }
        )
    }

    // 从「我的作品」跳转：打开指定类型与指定会话
    LaunchedEffect(openSessionId, openType) {
        val sessionId = openSessionId
        val type = openType
        if (sessionId != null && type != null) {
            FeatureTab.entries.firstOrNull { it.sessionType == type }?.let { tab ->
                currentFeature = tab
            }
            viewModel.switchFeature(type)
            viewModel.switchSession(sessionId)
        }
    }

    // 切换能力时同步 ViewModel 的活动类型（各能力会话独立保持）
    LaunchedEffect(currentFeature) {
        viewModel.switchFeature(currentFeature.sessionType)
    }

    val drawerSessions = uiState.sessions.filter { it.type == currentFeature.sessionType }

    LaunchedEffect(uiState.messages.size, uiState.isStreaming, streamingContent.length) {
        val count = uiState.messages.size + if (uiState.isStreaming) 1 else 0
        if (count > 0) {
            // 仅当用户已接近底部时自动跟随滚动，避免打断向上翻阅历史记录
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            val nearBottom = total == 0 || lastVisible >= total - 3
            if (nearBottom) {
                // 流式输出期间使用瞬时滚动，避免每个 token 触发动画打断导致卡顿
                if (uiState.isStreaming) {
                    listState.scrollToItem(count - 1)
                } else {
                    listState.animateScrollToItem(count - 1)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConversationDrawerContent(
                    sessions = drawerSessions,
                    currentSessionId = uiState.currentSessionId,
                    onSelect = { sessionId ->
                        viewModel.switchSession(sessionId)
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onRename = { sessionId, newTitle ->
                        viewModel.renameSession(sessionId, newTitle)
                    },
                    onNew = {
                        viewModel.newSession(currentFeature.sessionType)
                        scope.launch { drawerState.close() }
                    },
                    title = currentFeature.drawerTitle
                )
            }
        }
    ) {
        Scaffold(
            // 外层 AppNavHost 已统一处理底部导航栏，这里关闭 systemBars 内边距避免重复 padding
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ChatTopBar(
                    currentFeature = currentFeature,
                    onFeatureSelect = { currentFeature = it },
                    onOpenHistory = { scope.launch { drawerState.open() } }
                )
            },
            bottomBar = {
                if (currentFeature == FeatureTab.CHAT) {
                    ChatInputBar(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !uiState.isStreaming,
                        selectedImages = selectedImages,
                        onAddImage = ::onAddImageClick,
                        onRemoveImage = { path ->
                            selectedImages = selectedImages - path
                        },
                        onSend = {
                            val text = input
                            if (selectedImages.isEmpty()) {
                                if (viewModel.sendMessage(text)) {
                                    input = ""
                                }
                            } else if (viewModel.sendMessageWithImages(text, selectedImages)) {
                                input = ""
                                selectedImages = emptyList()
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentFeature) {
                FeatureTab.CHAT -> ChatPanel(
                    uiState = uiState,
                    streamingContent = streamingContent,
                    listState = listState,
                    onSuggestionClick = { prompt ->
                        input = prompt
                        if (viewModel.sendMessage(prompt)) {
                            input = ""
                        }
                    },
                    onCopy = { text ->
                        if (text.isBlank()) {
                            Toast.makeText(context, "无可复制内容", Toast.LENGTH_SHORT).show()
                        } else {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRegenerate = { viewModel.regenerateReply() },
                    onDelete = { messageId -> viewModel.deleteMessage(messageId) },
                    onResend = { text ->
                        if (viewModel.resendMessage(text)) {
                            input = ""
                        }
                    },
                    modifier = contentModifier
                )
                FeatureTab.IMAGE -> ImageGenerationPanel(
                    viewModel = generationViewModel,
                    messages = uiState.messages,
                    sessionId = uiState.currentSessionId,
                    modifier = contentModifier
                )
                FeatureTab.VIDEO -> VideoGenerationPanel(
                    viewModel = generationViewModel,
                    messages = uiState.messages,
                    sessionId = uiState.currentSessionId,
                    modifier = contentModifier
                )
            }
        }
    }
}

/** 顶部导航栏：历史按钮 + 功能切换 tabs */
@Composable
private fun ChatTopBar(
    currentFeature: FeatureTab,
    onFeatureSelect: (FeatureTab) -> Unit,
    onOpenHistory: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.Menu, contentDescription = "聊天记录")
            }
            FeatureTabs(
                currentFeature = currentFeature,
                onSelect = onFeatureSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeatureTabs(
    currentFeature: FeatureTab,
    onSelect: (FeatureTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FeatureTab.entries.forEach { feature ->
            val selected = feature == currentFeature
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(feature) }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feature.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatPanel(
    uiState: ChatUiState,
    streamingContent: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSuggestionClick: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDelete: (Long) -> Unit,
    onResend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.messages.isEmpty() && !uiState.isStreaming) {
        ChatWelcomeHint(
            onSuggestionClick = onSuggestionClick,
            modifier = modifier
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate,
                    onDelete = onDelete,
                    onResend = onResend
                )
            }
            if (uiState.isStreaming) {
                item(key = "streaming") {
                    StreamingBubble(streamingContent)
                }
            }
        }
    }
}

/** 空会话欢迎卡片 + 建议问题（对齐参考页面）。 */
@Composable
private fun ChatWelcomeHint(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "✨",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "你好，我是 AI 创作助手",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "我可以帮你写文案、生成图片、制作视频，试试下面的问题吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        val suggestions = listOf(
            "帮我写一篇产品介绍" to "📝 写一篇产品介绍",
            "生成一张赛博朋克风格的图片" to "🎨 赛博朋克风格图片",
            "生成一个日落海滩的视频" to "🎬 日落海滩视频"
        )
        suggestions.forEach { (prompt, label) ->
            SuggestionChip(
                text = label,
                onClick = { onSuggestionClick(prompt) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    selectedImages: List<String>,
    onAddImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit
) {
    // 底部导航栏由外层 Scaffold 顶起（NavigationBar 高度 = 80.dp + 导航栏 insets），
    // 输入栏只响应 IME 弹起的额外高度，避免与外层导航栏空间重复 padding。
    // 纯 IME 高度 = max(IME - (80.dp + 导航栏 insets), 0)，键盘收起时为 0。
    val density = LocalDensity.current
    val navBarHeight = with(density) { 80.dp.toPx() } + WindowInsets.navigationBars.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomInset = with(density) { (imeBottom - navBarHeight).coerceAtLeast(0f).toDp() }

    Column {
        HorizontalDivider()
        if (selectedImages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedImages, key = { it }) { path ->
                    PendingImageThumb(relativePath = path, onRemove = { onRemoveImage(path) })
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomInset)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAddImage,
                enabled = enabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "添加图片")
            }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
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

/** 待发送图片缩略图（输入区预览），点击右上角关闭移除。 */
@Composable
private fun PendingImageThumb(relativePath: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = File(context.filesDir, relativePath),
            contentDescription = "待发送图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(Color(0x99000000), RoundedCornerShape(11.dp))
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除图片",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: UiMessage,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDelete: (Long) -> Unit,
    onResend: (String) -> Unit
) {
    val isUser = message.role == "user"
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }
    val background = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var previewPath by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true }
            ),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = shape,
            color = background,
            contentColor = contentColor,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (message.imagePaths.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(message.imagePaths, key = { it }) { path ->
                            MessageImageThumb(path = path, onClick = { previewPath = path })
                        }
                    }
                }
                Text(
                    text = message.content.ifBlank { if (message.isError) "请求失败" else "..." },
                    modifier = Modifier.animateContentSize(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    menuExpanded = false
                    onCopy(message.content)
                }
            )
            if (message.role == "assistant") {
                DropdownMenuItem(
                    text = { Text("重新生成") },
                    onClick = {
                        menuExpanded = false
                        onRegenerate()
                    }
                )
            }
            if (isUser) {
                DropdownMenuItem(
                    text = { Text("重新发送") },
                    onClick = {
                        menuExpanded = false
                        onResend(message.content)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    menuExpanded = false
                    showDeleteConfirm = true
                }
            )
        }
    }

    previewPath?.let { path ->
        ImagePreviewDialog(relativePath = path, onDismiss = { previewPath = null })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除消息") },
            text = { Text("确定要删除这条消息吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(message.id)
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun StreamingBubble(content: String) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在思考...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/** 消息气泡内图片缩略图：加载失败时展示占位提示，不阻塞文本；点击放大预览。 */
@Composable
private fun MessageImageThumb(path: String, onClick: () -> Unit) {
    val context = LocalContext.current
    var failed by remember(path) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (failed) {
            Text(
                text = "图片加载失败",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
            )
        } else {
            AsyncImage(
                model = File(context.filesDir, path),
                contentDescription = "消息图片",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                contentScale = ContentScale.Crop,
                onError = { failed = true }
            )
        }
    }
}

/** 全屏图片预览弹窗，点击任意处关闭。 */
@Composable
private fun ImagePreviewDialog(relativePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(context.filesDir, relativePath),
                contentDescription = "图片预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}
