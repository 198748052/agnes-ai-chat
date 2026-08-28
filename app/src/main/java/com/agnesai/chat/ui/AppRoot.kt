package com.agnesai.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agnesai.chat.data.auth.AuthState
import com.agnesai.chat.di.AppContainer
import com.agnesai.chat.ui.auth.AuthViewModel
import com.agnesai.chat.ui.auth.LoginScreen
import com.agnesai.chat.ui.update.UpdateDialog
import com.agnesai.chat.ui.update.UpdateViewModel

/**
 * 应用根节点：
 * 1. 登录门禁：未登录时展示登录页，已登录进入主界面
 * 2. 全局弹窗：进入主界面后检查公告与更新（强制更新不可关闭）
 */
@Composable
fun AppRoot(appContainer: AppContainer) {
    val authViewModel: AuthViewModel = viewModel(factory = appContainer.authViewModelFactory)
    val updateViewModel: UpdateViewModel = viewModel(factory = appContainer.updateViewModelFactory)

    val authState by authViewModel.authState.collectAsState()

    when (authState) {
        AuthState.Checking -> SplashScreen()

        AuthState.LoggedOut -> LoginScreen(viewModel = authViewModel)

        is AuthState.LoggedIn -> {
            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdate()
            }

            AppNavHost(appContainer = appContainer)

            UpdateDialog(viewModel = updateViewModel)
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
