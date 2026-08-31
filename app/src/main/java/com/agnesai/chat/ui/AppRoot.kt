package com.agnesai.chat.ui

import androidx.compose.runtime.Composable
import com.agnesai.chat.di.AppContainer

/**
 * 应用根节点：直接进入主界面（无登录门禁）。
 */
@Composable
fun AppRoot(appContainer: AppContainer) {
    AppNavHost(appContainer = appContainer)
}
