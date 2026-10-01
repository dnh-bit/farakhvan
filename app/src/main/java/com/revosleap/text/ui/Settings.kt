package com.revosleap.text.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revosleap.text.BuildConfig
import com.revosleap.text.R
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(model: MainModel, sims: List<SimChoice>, loadSims: () -> Unit) {
    val settings by model.preferences.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.delay_seconds, settings.delay), style = MaterialTheme.typography.titleMedium)
        Slider(value = settings.delay.toFloat(), onValueChange = {
            model.preferences.update(settings.copy(delay = it.roundToInt()))
        }, valueRange = 1f..30f, steps = 28)
        Text(stringResource(R.string.delay_hint))
        SimSelector(model, sims, loadSims)
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
        listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (theme, label) ->
            Row(Modifier.fillMaxWidth().clickable { model.preferences.update(settings.copy(theme = theme)) },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.theme == theme, onClick = { model.preferences.update(settings.copy(theme = theme)) })
                Text(stringResource(label))
            }
        }
        ListItem(headlineContent = { Text(stringResource(R.string.extra_confirmation)) },
            supportingContent = { Text(stringResource(R.string.confirmation_hint)) },
            trailingContent = { Switch(checked = settings.confirmation,
                onCheckedChange = { model.preferences.update(settings.copy(confirmation = it)) }) })
        Text(stringResource(R.string.language_fa))
        Text(stringResource(R.string.version_info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        Text(stringResource(R.string.privacy_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.sent_not_delivered), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
