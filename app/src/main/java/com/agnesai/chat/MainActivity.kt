package com.agnesai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agnesai.chat.ui.AppRoot
import com.agnesai.chat.ui.theme.AgnesChatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as AgnesChatApplication).appContainer
        setContent {
            AgnesChatTheme {
                AppRoot(appContainer)
            }
        }
    }
}
