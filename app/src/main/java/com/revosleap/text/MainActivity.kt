package com.revosleap.text

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.revosleap.text.sending.SendController
import com.revosleap.text.ui.AppRoot

class MainActivity : ComponentActivity() {

    private var openCampaignId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            AppRoot(
                openCampaignId = openCampaignId,
                onOpenCampaignHandled = { openCampaignId = -1L }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(SendController.EXTRA_CAMPAIGN_ID, -1L) ?: -1L
        if (id >= 0) openCampaignId = id
    }
}
