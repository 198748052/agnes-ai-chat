package com.agnesai.chat.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.di.AppContainer
import com.agnesai.chat.ui.blog.BlogScreen
import com.agnesai.chat.ui.chat.ChatScreen
import com.agnesai.chat.ui.chat.ChatViewModel
import com.agnesai.chat.ui.auth.AuthViewModel
import com.agnesai.chat.ui.generation.GenerationViewModel
import com.agnesai.chat.ui.myworks.MyWorksScreen
import com.agnesai.chat.ui.myworks.MyWorksViewModel
import com.agnesai.chat.ui.profile.ProfileEditScreen
import com.agnesai.chat.ui.profile.ProfileEditViewModel
import com.agnesai.chat.ui.profile.ProfileScreen
import com.agnesai.chat.ui.profile.ProfileViewModel
import com.agnesai.chat.ui.settings.SettingsScreen
import com.agnesai.chat.ui.settings.SettingsViewModel
import com.agnesai.chat.ui.stats.StatsScreen
import com.agnesai.chat.ui.stats.StatsViewModel
import com.agnesai.chat.ui.storage.StorageScreen
import com.agnesai.chat.ui.storage.StorageViewModel

object Routes {
    const val CHAT = "chat"
    const val BLOG = "blog"
    const val PROFILE = "profile"
    const val PROFILE_EDIT = "profile_edit"
    const val SETTINGS = "settings"
    const val STORAGE = "storage"
    const val STATS = "stats"
    const val MY_WORKS = "my_works"

    /** 带会话跳转参数的聊天路由：chat?sessionId=xxx&type=xxx */
    const val CHAT_OPEN = "chat?sessionId={sessionId}&type={type}"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.CHAT, "聊天", Icons.AutoMirrored.Filled.Chat),
    BottomTab(Routes.BLOG, "博客", Icons.Filled.Language),
    BottomTab(Routes.PROFILE, "我的", Icons.Filled.Person)
)

@Composable
fun AppNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 底部导航栏常驻：键盘弹出时由输入法自然遮挡，避免显隐动画与 IME insets 动画叠加导致布局反复重排卡顿
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument("sessionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = SessionType.CHAT
                    }
                )
            ) { entry ->
                val chatViewModel: ChatViewModel = viewModel(factory = appContainer.chatViewModelFactory)
                val generationViewModel: GenerationViewModel =
                    viewModel(factory = appContainer.generationViewModelFactory)
                val openSessionId = entry.arguments?.getLong("sessionId") ?: -1L
                val openType = entry.arguments?.getString("type") ?: SessionType.CHAT
                ChatScreen(
                    viewModel = chatViewModel,
                    generationViewModel = generationViewModel,
                    openSessionId = openSessionId.takeIf { it > 0 },
                    openType = openType
                )
            }
            composable(Routes.BLOG) {
                BlogScreen()
            }
            composable(Routes.PROFILE) {
                val authViewModel: AuthViewModel = viewModel(factory = appContainer.authViewModelFactory)
                val profileViewModel: ProfileViewModel = viewModel(factory = appContainer.profileViewModelFactory)
                ProfileScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenStorage = { navController.navigate(Routes.STORAGE) },
                    onOpenMyWorks = { navController.navigate(Routes.MY_WORKS) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onEditProfile = { navController.navigate(Routes.PROFILE_EDIT) },
                    authViewModel = authViewModel,
                    profileViewModel = profileViewModel
                )
            }
            composable(Routes.PROFILE_EDIT) {
                val viewModel: ProfileEditViewModel =
                    viewModel(factory = appContainer.profileEditViewModelFactory)
                ProfileEditScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = appContainer.settingsViewModelFactory)
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.STORAGE) {
                val viewModel: StorageViewModel = viewModel(factory = appContainer.storageViewModelFactory)
                StorageScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.STATS) {
                val viewModel: StatsViewModel = viewModel(factory = appContainer.statsViewModelFactory)
                StatsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.MY_WORKS) {
                val viewModel: MyWorksViewModel = viewModel(factory = appContainer.myWorksViewModelFactory)
                MyWorksScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { work ->
                        // 跳转到聊天页对应类型与会话
                        navController.navigate(
                            Routes.CHAT_OPEN
                                .replace("{sessionId}", work.sessionId.toString())
                                .replace("{type}", work.type)
                        )
                    }
                )
            }
        }
    }
}
