package com.revosleap.text.ui

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revosleap.text.R
import com.revosleap.text.data.SendResult
import com.revosleap.text.data.Campaign
import com.revosleap.text.data.States
import com.revosleap.text.sending.SmsSendService

@Composable
fun HistoryScreen(model: MainModel, open: (Long) -> Unit) {
    val campaigns by remember { model.dao.campaigns() }.collectAsStateWithLifecycle(initialValue = null)
    if (campaigns == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (campaigns!!.isEmpty()) EmptyState(R.string.empty_history, R.string.history_hint)
    else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(campaigns!!, key = { it.id }) { campaign ->
            Card(Modifier.fillMaxWidth().clickable { open(campaign.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(campaign.target, style = MaterialTheme.typography.titleLarge)
                    Text(formattedDate(campaign.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(campaign.body, maxLines = 2)
                    Text(stringResource(R.string.campaign_totals, campaign.total, campaign.sent, campaign.failed))
                    Text(statusText(campaign.state), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CampaignScreen(id: Long, model: MainModel, gate: PermissionGate) {
    val context = LocalContext.current
    val campaign by remember(id) { model.dao.campaign(id) }
        .collectAsStateWithLifecycle(initialValue = Campaign(id = -1, target = "", body = ""))
    val rows by remember(id) { model.dao.results(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var cancel by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf<List<SendResult>?>(null) }
    var operation by remember { mutableStateOf("") }
    var resume by remember { mutableStateOf(false) }
    var fullMessage by remember { mutableStateOf(false) }
    val titleShare = stringResource(R.string.share_csv)
    val validFailures = rows.filter { it.status == States.FAILED && it.valid && it.reason !in listOf("invalid", "empty") }
    val isActive = campaign?.state in listOf(States.RUNNING, States.PAUSED)
    val start: () -> Unit = {
        model.run {
            try { SmsSendService.command(context, id) }
            catch (_: Exception) {
                model.dao.campaignState(id, States.INTERRUPTED)
                model.notify(R.string.error_start_service)
            }
        }
    }
    if (campaign == null) {
        EmptyState(R.string.campaign_missing)
        return
    }
    if (campaign?.id == -1L) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(campaign!!.target, style = MaterialTheme.typography.titleLarge)
        val done = rows.count { it.status in listOf(States.SENT, States.FAILED, States.UNKNOWN) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { if (rows.isEmpty()) 0f else done.toFloat() / rows.size },
                    modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp)
                Text(stringResource(R.string.progress_fraction, done, rows.size),
                    style = MaterialTheme.typography.titleMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(statusText(campaign!!.state), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.campaign_totals, rows.size,
                    rows.count { it.status == States.SENT }, rows.count { it.status == States.FAILED }))
            }
        }
        Text(campaign!!.body, maxLines = 3, modifier = Modifier.clickable { fullMessage = true })
        if (rows.any { it.status == States.UNKNOWN }) Text(stringResource(R.string.uncertain_warning),
            color = MaterialTheme.colorScheme.error)
        else if (campaign!!.state == States.INTERRUPTED) Text(stringResource(R.string.interrupted_hint),
            color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.sent_not_delivered), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.id }) { row ->
                val canRetry = !isActive && row.valid && row.status in listOf(States.FAILED, States.UNKNOWN) &&
                    row.reason !in listOf("invalid", "empty")
                ListItem(modifier = Modifier.clickable(enabled = canRetry) { operation = "one"; retry = listOf(row) },
                    headlineContent = { PhoneText(row.number, !row.valid) },
                    supportingContent = {
                        Column {
                            if (row.name.isNotBlank()) Text(row.name)
                            Text(statusText(row.status))
                            if (row.reason.isNotBlank()) Text(reasonText(row), color = MaterialTheme.colorScheme.error)
                            if (row.timestamp > 0) Text(formattedDate(row.timestamp))
                            if (canRetry) Text(stringResource(R.string.tap_retry), color = MaterialTheme.colorScheme.primary)
                        }
                    }, leadingContent = {
                        val icon = when (row.status) {
                            States.SENT -> Icons.Default.CheckCircle
                            States.FAILED, States.UNKNOWN -> Icons.Default.Error
                            States.SENDING -> Icons.Default.MoreHoriz
                            else -> Icons.Default.HourglassEmpty
                        }
                        Icon(icon, statusText(row.status), tint = if (row.status in listOf(States.FAILED, States.UNKNOWN))
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    })
            }
        }
        if (isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    model.run {
                        SmsSendService.command(context, id,
                            if (campaign!!.state == States.PAUSED) SmsSendService.RESUME else SmsSendService.PAUSE)
                    }
                }) { Text(stringResource(if (campaign!!.state == States.PAUSED) R.string.resume else R.string.pause)) }
                TextButton(onClick = { cancel = true }) {
                    Text(stringResource(R.string.cancel_campaign), color = MaterialTheme.colorScheme.error)
                }
            }
        } else if (rows.any { it.status == States.PENDING }) {
            OutlinedButton(onClick = { resume = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.resume_unsent))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (validFailures.isNotEmpty() && !isActive) OutlinedButton(onClick = {
                operation = "failed"; retry = validFailures
            }) { Text(stringResource(R.string.retry_failed, validFailures.size)) }
            TextButton(onClick = {
                model.run { context.startActivity(Intent.createChooser(model.repository.export(id), titleShare)) }
            }) { Text(stringResource(R.string.share_csv)) }
        }
    }
    if (fullMessage) AlertDialog(onDismissRequest = { fullMessage = false },
        title = { Text(stringResource(R.string.message)) },
        text = { Text(campaign!!.body, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { fullMessage = false }) { Text(stringResource(R.string.back)) } })
    if (cancel) ConfirmDialog(R.string.cancel_campaign, stringResource(R.string.cancel_campaign_question),
        onDismiss = { cancel = false }, onConfirm = {
            cancel = false
            model.run { SmsSendService.command(context, id, SmsSendService.CANCEL) }
        })
    if (resume) SendRowsDialog(rows.filter { it.status == States.PENDING }, onDismiss = { resume = false },
        onConfirm = {
            resume = false
            if (SmsSendService.alive) model.notify(R.string.queue_busy)
            else gate(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS), R.string.permission_sms, start)
        })
    retry?.let { chosen ->
        SendRowsDialog(chosen, onDismiss = { retry = null }, onConfirm = {
            retry = null
            if (SmsSendService.alive) model.notify(R.string.queue_busy)
            else gate(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS), R.string.permission_sms) {
                model.run {
                    if (operation == "failed") model.dao.retryFailed(id)
                    else chosen.firstOrNull()?.let { model.dao.retryOne(it.id) }
                    try { SmsSendService.command(context, id) }
                    catch (_: Exception) {
                        model.dao.campaignState(id, States.INTERRUPTED)
                        model.notify(R.string.error_start_service)
                    }
                }
            }
        })
    }
}

@Composable
fun SendRowsDialog(rows: List<SendResult>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_send)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.confirm_cost, rows.size, rows.sumOf { smsParts(it.renderedBody) }))
                if (rows.any { it.status == States.UNKNOWN }) Text(stringResource(R.string.unknown_retry_warning),
                    color = MaterialTheme.colorScheme.error)
                rows.take(5).forEach { row ->
                    PhoneText(row.number, !row.valid)
                    Text(row.renderedBody)
                }
                if (rows.size > 5) Text(stringResource(R.string.more_numbers, rows.size - 5))
                Text(stringResource(R.string.sms_charges))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
