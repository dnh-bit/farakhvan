package com.revosleap.text.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revosleap.text.R
import com.revosleap.text.data.AppDatabase
import com.revosleap.text.data.CampaignStatus
import com.revosleap.text.data.CampaignWithCounts
import com.revosleap.text.data.ResultStatus
import com.revosleap.text.data.SendResultEntity
import com.revosleap.text.sending.SendController
import com.revosleap.text.util.Formatting
import com.revosleap.text.util.ltr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live progress while sending AND the history detail view of a finished campaign. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(campaignId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val campaignState by remember(campaignId) { db.campaignDao().observeOne(campaignId) }.collectLoad()
    val resultsState by remember(campaignId) { db.resultDao().observe(campaignId) }.collectLoad()
    val active by SendController.active.collectAsState()

    var confirmCancel by remember { mutableStateOf(false) }
    var retryRow by remember { mutableStateOf<SendResultEntity?>(null) }

    val busyMsg = stringResource(R.string.send_busy)
    val exportFailedMsg = stringResource(R.string.export_failed)
    val shareTitle = stringResource(R.string.export_csv)

    fun startAgain(resetFailed: Boolean, onlyRowId: Long?) {
        scope.launch {
            val current = SendController.active.value
            if (current != null && current != campaignId) {
                snackbar.showSnackbar(busyMsg)
                return@launch
            }
            if (resetFailed) db.resultDao().resetFailed(campaignId)
            if (onlyRowId != null) db.resultDao().resetOne(onlyRowId)
            if (current == null) {
                db.campaignDao().setStatus(campaignId, CampaignStatus.RUNNING)
                if (!SendController.start(context, campaignId)) {
                    db.campaignDao().setStatus(campaignId, CampaignStatus.INTERRUPTED)
                }
            }
        }
    }

    fun exportCsv() {
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) {
                    val rows = db.resultDao().getAll(campaignId)
                    Formatting.csvShareIntent(context, campaignId, rows, shareTitle)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                snackbar.showSnackbar(exportFailedMsg)
            }
        }
    }

    val campaign: CampaignWithCounts? = (campaignState as? Load.Ready)?.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(campaign?.campaign?.targetName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { exportCsv() }, enabled = campaign != null) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export_csv))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val cs = campaignState) {
                is Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorBox()
                is Load.Ready -> {
                    val c = cs.value
                    if (c == null) {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.campaign_not_found)
                        )
                    } else {
                        val status = if (
                            (c.campaign.status == CampaignStatus.RUNNING || c.campaign.status == CampaignStatus.PAUSED) &&
                            active != c.campaign.id
                        ) {
                            CampaignStatus.INTERRUPTED
                        } else {
                            c.campaign.status
                        }
                        val results = (resultsState as? Load.Ready)?.value.orEmpty()
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            item(key = "header") {
                                CampaignHeader(
                                    c = c,
                                    status = status,
                                    onPause = { SendController.pause(context) },
                                    onResume = { SendController.resume(context) },
                                    onCancel = { confirmCancel = true },
                                    onResumeUnsent = { startAgain(false, null) },
                                    onRetryFailed = { startAgain(true, null) }
                                )
                            }
                            if (resultsState is Load.Loading) {
                                item(key = "loading") {
                                    Box(Modifier.fillMaxWidth().height(120.dp)) { LoadingBox() }
                                }
                            } else if (resultsState is Load.Failed) {
                                item(key = "error") {
                                    Box(Modifier.fillMaxWidth().height(200.dp)) { ErrorBox() }
                                }
                            } else if (results.isEmpty()) {
                                item(key = "empty") {
                                    Box(Modifier.fillMaxWidth().height(200.dp)) {
                                        EmptyState(
                                            icon = Icons.Filled.History,
                                            title = stringResource(R.string.results_empty)
                                        )
                                    }
                                }
                            } else {
                                items(results, key = { it.id }) { r ->
                                    ResultRow(r, onClick = {
                                        if (r.status == ResultStatus.FAILED) retryRow = r
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = stringResource(R.string.cancel_confirm_title),
            text = stringResource(R.string.cancel_confirm_text),
            confirmLabel = stringResource(R.string.action_cancel_send),
            destructive = true,
            onDismiss = { confirmCancel = false },
            onConfirm = {
                confirmCancel = false
                SendController.cancel(context)
            }
        )
    }

    val rowToRetry = retryRow
    if (rowToRetry != null) {
        ConfirmDialog(
            title = stringResource(R.string.retry_one_title),
            text = stringResource(
                R.string.retry_one_text,
                (rowToRetry.name.ifBlank { rowToRetry.number }).ltr()
            ),
            confirmLabel = stringResource(R.string.action_send_again),
            onDismiss = { retryRow = null },
            onConfirm = {
                retryRow = null
                startAgain(false, rowToRetry.id)
            }
        )
    }
}

@Composable
private fun CampaignHeader(
    c: CampaignWithCounts,
    status: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onResumeUnsent: () -> Unit,
    onRetryFailed: () -> Unit
) {
    val done = c.sent + c.failed
    val pending = (c.total - done).coerceAtLeast(0)
    val progress = if (c.total == 0) 0f else done.toFloat() / c.total

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.progress_of, done, c.total),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    statusLabel(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            stringResource(R.string.campaign_counts, c.sent, c.failed, pending),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            Formatting.dateTime(c.campaign.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Controls
        when (status) {
            CampaignStatus.RUNNING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_pause))
                    }
                    CancelButton(onCancel, Modifier.weight(1f))
                }
            }

            CampaignStatus.PAUSED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_resume))
                    }
                    CancelButton(onCancel, Modifier.weight(1f))
                }
            }

            CampaignStatus.INTERRUPTED -> {
                Button(
                    onClick = onResumeUnsent,
                    enabled = pending > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_resume_unsent))
                }
            }
        }

        if (status != CampaignStatus.RUNNING && status != CampaignStatus.PAUSED && c.failed > 0) {
            FilledTonalButton(
                onClick = onRetryFailed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_retry_failed, c.failed))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.confirm_message), style = MaterialTheme.typography.labelLarge)
                Text(c.campaign.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.Filled.Stop, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.action_cancel_send))
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        CampaignStatus.RUNNING -> R.string.status_running
        CampaignStatus.PAUSED -> R.string.status_paused
        CampaignStatus.CANCELLED -> R.string.status_cancelled
        CampaignStatus.INTERRUPTED -> R.string.status_interrupted
        else -> R.string.status_done
    }
)

@Composable
private fun ResultRow(r: SendResultEntity, onClick: () -> Unit) {
    val failed = r.status == ResultStatus.FAILED
    ListItem(
        modifier = Modifier.clickable(enabled = failed, onClick = onClick),
        leadingContent = {
            when (r.status) {
                ResultStatus.SENT -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.result_sent),
                    tint = StatusColors.sent
                )

                ResultStatus.FAILED -> Icon(
                    Icons.Filled.Error,
                    contentDescription = stringResource(R.string.result_failed),
                    tint = MaterialTheme.colorScheme.error
                )

                ResultStatus.SENDING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )

                else -> Icon(
                    Icons.Filled.Schedule,
                    contentDescription = stringResource(R.string.result_pending),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        },
        headlineContent = {
            Text(if (r.name.isNotBlank()) r.name else r.number.ltr())
        },
        supportingContent = {
            Column {
                if (r.name.isNotBlank()) Text(r.number.ltr())
                if (failed && r.reason.isNotBlank()) {
                    Text(r.reason, color = MaterialTheme.colorScheme.error)
                }
                if (r.updatedAt > 0 && (failed || r.status == ResultStatus.SENT)) {
                    Text(
                        Formatting.dateTime(r.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            if (failed) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_send_again),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
