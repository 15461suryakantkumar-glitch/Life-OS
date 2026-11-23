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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Step 1: Configure App Check with launch intent before Compose UI or ViewModels
        configureAppCheck(this, intent)
        widgetRouteRequest = intent?.getStringExtra("navigate_to")

        enableEdgeToEdge()
        setContent {
            LifeOsApp(
                initialRoute = widgetRouteRequest,
                onConsumeInitialRoute = { widgetRouteRequest = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("navigate_to")?.let { route ->
            widgetRouteRequest = route
        }
    }
}
