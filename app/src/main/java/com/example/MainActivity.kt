package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.ai.configureAppCheck
import com.example.ui.LifeOsApp

class MainActivity : ComponentActivity() {
    private var widgetRouteRequest by mutableStateOf<String?>(null)
    private var incomingFriendInviteUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure App Check gracefully only when Firebase is initialized
        configureAppCheck(this, intent)
        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            LifeOsApp(
                initialRoute = widgetRouteRequest,
                onConsumeInitialRoute = { widgetRouteRequest = null },
                incomingFriendPayload = incomingFriendInviteUri,
                onConsumeIncomingFriendPayload = { incomingFriendInviteUri = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.getStringExtra("navigate_to")?.let { route ->
            widgetRouteRequest = when (route.lowercase()) {
                "friends", "together", "together_one" -> "together_one"
                "suru_ai", "life_ai" -> "life_ai"
                else -> route
            }
        }
        val dataUri = intent?.data
        val isFriendDeepLink = dataUri != null && (
            (dataUri.scheme == "lifeos" && dataUri.host == "friend") ||
                (dataUri.scheme == "feelora" && dataUri.host == "invite") ||
                (dataUri.scheme == "https" && dataUri.host == "feelora.app" && dataUri.path?.startsWith("/invite") == true)
            )
        if (isFriendDeepLink) {
            incomingFriendInviteUri = dataUri.toString()
            widgetRouteRequest = "together_one"
        }
    }
}
