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
            widgetRouteRequest = route
        }
        val dataUri = intent?.data
        if (dataUri != null && dataUri.scheme == "lifeos" && dataUri.host == "friend") {
            incomingFriendInviteUri = dataUri.toString()
            widgetRouteRequest = "friends"
        }
    }
}
