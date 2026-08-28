package com.agnesai.chat.ui.myworks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.works.MyWork
import com.agnesai.chat.ui.common.formatTimestamp
import com.agnesai.chat.ui.common.videoThumbnailFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWorksScreen(
    viewModel: MyWorksViewModel,
    onBack: () -> Unit,
    onOpenConversation: (MyWork) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的作品") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val error = state.error
            when {
                state.loading && state.works.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null && state.works.isEmpty() -> {
                    ErrorState(
                        message = error,
                        onRetry = { viewModel.load() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    WorksContent(
                        state = state,
                        onFilter = viewModel::setFilter,
                        onWorkClick = viewModel::openDetail
                    )
                }
            }
        }
    }

    state.detailWork?.let { work ->
        MyWorksDetail(
            work = work,
            onDismiss = viewModel::closeDetail,
            onDelete = { viewModel.requestDelete(work) },
            onOpenConversation = {
                viewModel.closeDetail()
                onOpenConversation(work)
            }
        )
    }

    if (state.pendingDeleteWork != null) {
        DeleteConfirmDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = viewModel::cancelDelete
        )
    }
}

@Composable
private fun WorksContent(
    state: MyWorksUiState,
    onFilter: (WorkFilter) -> Unit,
    onWorkClick: (MyWork) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FilterBar(current = state.filter, onFilter = onFilter)

        if (state.visibleWorks.isEmpty()) {
            EmptyState(
                hasAny = state.works.isNotEmpty(),
                filter = state.filter,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.visibleWorks, key = { it.id }) { work ->
                    WorkCard(work = work, onClick = { onWorkClick(work) })
                }
            }
        }
    }
}

@Composable
private fun FilterBar(current: WorkFilter, onFilter: (WorkFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkFilter.entries.forEach { filter ->
            FilterChip(
                selected = current == filter,
                onClick = { onFilter(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun WorkCard(work: MyWork, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (work.type == SessionType.VIDEO) {
                    VideoThumbnailCard(url = work.url)
                } else {
                    AsyncImage(
                        model = work.url,
                        contentDescription = "作品图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                TypeBadge(type = work.type, modifier = Modifier.align(Alignment.TopStart))
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = work.sessionTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimestamp(work.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnailCard(url: String) {
    val context = LocalContext.current
    val thumbnail by produceState<File?>(initialValue = null, url) {
        value = videoThumbnailFile(context, url)
    }
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
                shape = CircleShape,
                color = Color(0x99000000),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    } else {
        VideoCover()
    }
}

@Composable
private fun VideoCover() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x33000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "视频",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String, modifier: Modifier = Modifier) {
    val (icon, label) = if (type == SessionType.VIDEO) {
        Icons.Filled.Videocam to "视频"
    } else {
        Icons.Filled.Image to "图片"
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0x99000000),
        contentColor = Color.White,
        modifier = modifier.padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EmptyState(
    hasAny: Boolean,
    filter: WorkFilter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasAny) {
                when (filter) {
                    WorkFilter.ALL -> "暂无作品"
                    WorkFilter.IMAGE -> "暂无图片作品"
                    WorkFilter.VIDEO -> "暂无视频作品"
                }
            } else {
                "暂无作品，去创作你的第一个 AI 作品吧"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除作品") },
        text = { Text("删除后该作品将从我的作品中移除，此操作不可恢复。是否继续？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
