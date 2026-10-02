package com.farakhvan.text.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.farakhvan.text.BuildConfig
import com.farakhvan.text.R
import com.farakhvan.text.data.Settings
import com.farakhvan.text.util.SmsUtil
import com.farakhvan.text.util.hasPermission
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }

    var phonePermission by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.READ_PHONE_STATE))
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        phonePermission = it
    }
    val sims = remember(phonePermission) { SmsUtil.activeSims(context) }
    val looksDual = remember { SmsUtil.looksDualSim(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Delay between messages
            SectionHeader(stringResource(R.string.settings_delay_title))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_delay_value, settings.delaySec))
                Slider(
                    value = settings.delaySec.toFloat(),
                    onValueChange = { settings.delaySec = it.roundToInt() },
                    valueRange = 1f..30f,
                    steps = 28
                )
                Text(
                    stringResource(R.string.settings_delay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Default SIM
            SectionHeader(stringResource(R.string.settings_sim_title))
            if (sims.size >= 2) {
                sims.forEach { sim ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { settings.defaultSim = sim.slot }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.defaultSim == sim.slot, onClick = null)
                        Text(
                            stringResource(R.string.sim_slot, sim.slot + 1, sim.label),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            } else if (looksDual && !phonePermission) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.sim_permission_explain))
                    TextButton(onClick = { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) }) {
                        Text(stringResource(R.string.sim_permission_allow))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.settings_sim_single),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Theme
            SectionHeader(stringResource(R.string.settings_theme_title))
            val themes = listOf(
                R.string.theme_system,
                R.string.theme_light,
                R.string.theme_dark
            )
            themes.forEachIndexed { index, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { settings.theme = index }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.theme == index, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Language (Persian is the single UI language)
            SectionHeader(stringResource(R.string.settings_language_title))
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_persian)) },
                supportingContent = { Text(stringResource(R.string.settings_language_note)) }
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Confirmation toggle
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_confirm_title)) },
                supportingContent = { Text(stringResource(R.string.settings_confirm_hint)) },
                trailingContent = {
                    Switch(
                        checked = settings.confirmBeforeSend,
                        onCheckedChange = { settings.confirmBeforeSend = it }
                    )
                },
                modifier = Modifier.clickable { settings.confirmBeforeSend = !settings.confirmBeforeSend }
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // About
            SectionHeader(stringResource(R.string.settings_about_title))
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                )
                Text(
                    stringResource(R.string.about_offline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_license),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_font),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
