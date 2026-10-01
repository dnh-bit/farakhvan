package com.revosleap.text.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revosleap.text.R
import com.revosleap.text.data.Numbers
import com.revosleap.text.data.Recipient
import com.revosleap.text.sending.SmsSendService

data class SendPreview(val target: String, val groupId: Long?, val body: String,
    val recipients: List<Recipient>, val duplicates: Int)

fun smsParts(body: String): Int = if (body.isBlank()) 0 else SmsMessage.calculateLength(body, false)[0]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(model: MainModel, gate: PermissionGate, sims: List<SimChoice>,
    loadSims: () -> Unit, open: (Long) -> Unit) {
    val context = LocalContext.current
    val groups by remember { model.dao.groups() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by model.preferences.settings.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(0) }
    var groupId by rememberSaveable { mutableStateOf(0L) }
    val members by remember(groupId) { model.dao.members(groupId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var paste by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<List<Recipient>>(emptyList()) }
    var groupMenu by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<Recipient>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SendPreview?>(null) }
    var extraConfirm by remember { mutableStateOf<SendPreview?>(null) }
    val pastedTarget = stringResource(R.string.pasted_target)
    val contactsTarget = stringResource(R.string.contacts_target)
    val source = when (mode) {
        0 -> members.map { Recipient(it.number, it.name, it.valid) }
        1 -> Numbers.parse(paste)
        else -> picked
    }
    val recipients = Numbers.deduplicate(source)
    val target = if (mode == 0) groups.firstOrNull { it.id == groupId }?.name.orEmpty()
        else if (mode == 1) pastedTarget else contactsTarget
    val calculate = if (body.isBlank()) intArrayOf(0, 0, 0, 1) else SmsMessage.calculateLength(body, false)
    val prepare: () -> Unit = {
        loading = true
        model.run {
            try {
                if (SmsSendService.alive) model.notify(R.string.queue_busy)
                else {
                    val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    preview = SendPreview(target, if (mode == 0) groupId else null, body,
                        model.enrich(recipients, canRead), source.size - recipients.size)
                }
            } finally { loading = false }
        }
    }
    val dispatch: (SendPreview) -> Unit = { send ->
        preview = null
        extraConfirm = null
        gate(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS), R.string.permission_sms) {
            loading = true
            model.run {
                try {
                    if (SmsSendService.alive) model.notify(R.string.queue_busy)
                    else {
                        val id = model.repository.createCampaign(send.target, send.body, send.recipients,
                            settings, send.groupId)
                        try { SmsSendService.command(context, id) }
                        catch (_: Exception) { model.dao.campaignState(id, com.revosleap.text.data.States.INTERRUPTED); model.notify(R.string.error_start_service) }
                        open(id)
                    }
                } finally { loading = false }
            }
        }
    }
    Scaffold(bottomBar = {
        Button(onClick = prepare, enabled = body.isNotBlank() && recipients.any { it.valid } && !loading,
            modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, null)
            Text(stringResource(R.string.review_send), Modifier.padding(horizontal = 8.dp))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(R.string.group_target, R.string.pasted_target, R.string.contacts_target).forEachIndexed { index, label ->
                    SegmentedButton(selected = mode == index, onClick = { mode = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(stringResource(label)) }
                }
            }
            when (mode) {
                0 -> {
                    Column {
                        OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (target.isBlank()) stringResource(R.string.choose_group) else target)
                        }
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                            groups.forEach { group ->
                                DropdownMenuItem(text = { Text(group.name) },
                                    onClick = { groupId = group.id; groupMenu = false })
                            }
                        }
                        if (groups.isEmpty()) Text(stringResource(R.string.first_group))
                    }
                }
                1 -> OutlinedTextField(paste, { paste = it }, label = { Text(stringResource(R.string.paste_numbers)) },
                    supportingText = { Text(stringResource(R.string.paste_hint)) }, minLines = 4,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp))
                else -> OutlinedButton(onClick = {
                    gate(arrayOf(Manifest.permission.READ_CONTACTS), R.string.permission_contacts) {
                        loading = true
                        model.run {
                            try { contacts = model.repository.contacts() }
                            finally { loading = false }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pick_contacts)) }
            }
            Text(stringResource(R.string.recipients_summary, recipients.size, recipients.count { !it.valid }),
                color = if (recipients.any { !it.valid }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (source.size != recipients.size) Text(stringResource(R.string.duplicates_removed, source.size - recipients.size))
            if (recipients.isNotEmpty()) Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                recipients.forEach { recipient ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (recipient.name.isNotBlank()) Text(recipient.name)
                        PhoneText(recipient.number, !recipient.valid)
                    }
                }
            }
            OutlinedTextField(body, { body = it.take(5000) }, label = { Text(stringResource(R.string.message)) },
                minLines = 5, modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(stringResource(R.string.segment_counter, calculate[0],
                        if (calculate[3] == 1) stringResource(R.string.encoding_gsm) else stringResource(R.string.encoding_unicode),
                        calculate[2]))
                })
            FilterChip(selected = false, onClick = { body += "{name}" },
                label = { Text(stringResource(R.string.name_token)) })
            if (body.contains("{name}")) Text(stringResource(R.string.personalization_hint))
            SimSelector(model, sims, loadSims)
            Text(stringResource(R.string.sms_charges), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) CircularProgressIndicator()
        }
    }
    contacts?.let { rows -> RecipientPicker(R.string.pick_contacts, rows, onDismiss = { contacts = null }) {
        picked = it
        contacts = null
    } }
    preview?.let { send ->
        ModalBottomSheet(onDismissRequest = { preview = null }) {
            val valid = send.recipients.filter { it.valid && send.body.replace("{name}", it.name).isNotBlank() }
            val totalParts = valid.sumOf { smsParts(send.body.replace("{name}", it.name)) }
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.confirm_send), style = MaterialTheme.typography.headlineSmall)
                Text(send.target, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.confirm_cost, valid.size, totalParts))
                val skipped = send.recipients.size - valid.size
                if (skipped > 0) Text(stringResource(R.string.skipped_invalid, skipped), color = MaterialTheme.colorScheme.error)
                if (send.duplicates > 0) Text(stringResource(R.string.duplicates_removed, send.duplicates))
                send.recipients.take(5).forEach { recipient ->
                    PhoneText(recipient.number, !recipient.valid)
                }
                if (send.recipients.size > 5) Text(stringResource(R.string.more_numbers, send.recipients.size - 5))
                Text(send.body, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.personalized_cost_hint))
                Text(stringResource(R.string.sms_charges), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    if (settings.confirmation) { preview = null; extraConfirm = send }
                    else dispatch(send)
                }, enabled = valid.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.send)) }
                TextButton(onClick = { preview = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    extraConfirm?.let { send ->
        ConfirmDialog(R.string.confirm_send, stringResource(R.string.final_send_question, send.recipients.count { it.valid }),
            onDismiss = { extraConfirm = null }, onConfirm = { dispatch(send) })
    }
}

@Composable
fun SimSelector(model: MainModel, sims: List<SimChoice>, load: () -> Unit) {
    val settings by model.preferences.settings.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sim_title), style = MaterialTheme.typography.titleMedium)
        if (sims.isEmpty()) TextButton(onClick = load) { Text(stringResource(R.string.enable_sim_picker)) }
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sims.forEach { sim ->
                FilterChip(selected = settings.sim == sim.id, onClick = { model.preferences.update(settings.copy(sim = sim.id)) },
                    label = { Text(stringResource(R.string.sim_number, sim.slot)) })
            }
        }
        if (sims.isEmpty()) Text(stringResource(R.string.sim_default_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
