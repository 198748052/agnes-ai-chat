package com.agnesai.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private val modelOptions = listOf(
    "agnes-2.5-flash" to "Agnes 2.5 Flash（快速）",
    "agnes-2.5-pro" to "Agnes 2.5 Pro（高质量）",
    "agnes-2.0-flash" to "Agnes 2.0 Flash（兼容）"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            snackbarHostState.showSnackbar("设置已保存")
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        // 外层 AppNavHost 已统一处理底部导航栏，关闭 systemBars 内边距避免重复 padding
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // 底部导航栏由外层 Scaffold 顶起（NavigationBar 高度 = 80.dp + 导航栏 insets），
        // 内容只响应 IME 弹起的额外高度，避免与外层导航栏空间重复 padding。
        val density = LocalDensity.current
        val navBarHeight = with(density) { 80.dp.toPx() } + WindowInsets.navigationBars.getBottom(density)
        val imeBottom = WindowInsets.ime.getBottom(density)
        val bottomInset = with(density) { (imeBottom - navBarHeight).coerceAtLeast(0f).toDp() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomInset)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "API 地址",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text("https://api.agnes-ai.cn/") },
                isError = uiState.baseUrlError != null,
                supportingText = uiState.baseUrlError?.let { { Text(it) } },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::onResetBaseUrl) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "恢复默认"
                        )
                    }
                }
            )
            Text(
                text = "兼容 OpenAI 协议的服务端点；留空保存后使用默认地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "API Key",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Agnes API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "隐藏" else "显示"
                        )
                    }
                }
            )
            Text(
                text = "在 https://www.agnes-ai.cn 获取你的 API Key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "系统提示词",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = uiState.systemPrompt,
                onValueChange = viewModel::onSystemPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("System Prompt") },
                minLines = 4,
                maxLines = 8
            )
            Text(
                text = "设置 AI 助手的角色与行为风格",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "模型",
                style = MaterialTheme.typography.titleMedium
            )
            ModelSelector(
                modelName = uiState.modelName,
                onModelChange = viewModel::onModelChange
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "生成参数",
                style = MaterialTheme.typography.titleMedium
            )
            ParameterSlider(
                label = "Temperature（随机性）",
                value = uiState.temperature,
                range = 0f..2f,
                steps = 19,
                onValueChange = viewModel::onTemperatureChange
            )
            ParameterSlider(
                label = "Top P（核采样）",
                value = uiState.topP,
                range = 0f..1f,
                steps = 9,
                onValueChange = viewModel::onTopPChange
            )
            OutlinedTextField(
                value = uiState.maxTokensInput,
                onValueChange = viewModel::onMaxTokensChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Max Tokens（可选）") },
                placeholder = { Text("留空表示不限制") },
                isError = uiState.maxTokensError != null,
                supportingText = uiState.maxTokensError?.let { { Text(it) } },
                singleLine = true
            )
            Text(
                text = "参数修改后对所有会话的新请求即时生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    modelName: String,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = modelOptions.firstOrNull { it.first == modelName }?.second ?: modelName

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("聊天模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            modelOptions.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onModelChange(id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.US, "%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
