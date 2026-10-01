package com.revosleap.text

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import com.revosleap.text.ui.FarakhvanRoot
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val openedCampaign = mutableLongStateOf(-1)
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration)
        val locale = Locale("fa")
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedCampaign.longValue = intent.getLongExtra("campaign", -1)
        enableEdgeToEdge()
        setContent { FarakhvanRoot(openedCampaign.longValue) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openedCampaign.longValue = intent.getLongExtra("campaign", -1)
    }
}
