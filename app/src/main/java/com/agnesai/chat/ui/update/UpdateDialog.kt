package com.agnesai.chat.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val info = uiState.updateInfo ?: return

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = {
            Text(text = "发现新版本 v${info.latestVersionName}")
        },
        text = {
            Text(
                text = info.updateLog.ifBlank { "有新的版本可用，请升级到最新版本以获得更好的体验。" },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::download) {
                Text("立即更新")
            }
        },
        dismissButton = {
            // 强制更新时不可关闭
            if (!info.forceUpdate) {
                TextButton(onClick = viewModel::dismiss) {
                    Text("以后再说")
                }
            }
        }
    )
}
