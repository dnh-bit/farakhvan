package com.farakhvan.text.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farakhvan.text.R
import com.farakhvan.text.data.AppDatabase
import com.farakhvan.text.data.CampaignStatus
import com.farakhvan.text.data.CampaignWithCounts
import com.farakhvan.text.sending.SendController
import com.farakhvan.text.util.Formatting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenCampaign: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state by remember { db.campaignDao().observeAll() }.collectLoad()
    val active by SendController.active.collectAsState()
    var toDelete by remember { mutableStateOf<CampaignWithCounts?>(null) }
    val runningMsg = stringResource(R.string.history_delete_running)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val s = state) {
                is Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorBox()
                is Load.Ready -> {
                    if (s.value.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.history_empty_title),
                            subtitle = stringResource(R.string.history_empty_subtitle)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.value, key = { it.campaign.id }) { item ->
                                CampaignCard(
                                    item = item,
                                    onClick = { onOpenCampaign(item.campaign.id) },
                                    onDelete = {
                                        if (active == item.campaign.id) {
                                            scope.launch { snackbar.showSnackbar(runningMsg) }
                                        } else {
                                            toDelete = item
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pending = toDelete
    if (pending != null) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            text = stringResource(R.string.history_delete_confirm, pending.campaign.targetName),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                scope.launch { db.campaignDao().delete(pending.campaign.id) }
            }
        )
    }
}

@Composable
private fun CampaignCard(item: CampaignWithCounts, onClick: () -> Unit, onDelete: () -> Unit) {
    val c = item.campaign
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.targetName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Formatting.dateTime(c.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                c.message,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.history_total, item.total)) }
                )
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.history_sent, item.sent)) }
                )
                if (item.failed > 0) {
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text(
                                stringResource(R.string.history_failed, item.failed),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
            val statusRes = when (c.status) {
                CampaignStatus.RUNNING -> R.string.status_running
                CampaignStatus.PAUSED -> R.string.status_paused
                CampaignStatus.CANCELLED -> R.string.status_cancelled
                CampaignStatus.INTERRUPTED -> R.string.status_interrupted
                else -> R.string.status_done
            }
            Text(
                stringResource(statusRes),
                style = MaterialTheme.typography.labelMedium,
                color = if (c.status == CampaignStatus.INTERRUPTED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}
