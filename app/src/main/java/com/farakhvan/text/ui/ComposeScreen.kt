package com.farakhvan.text.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.farakhvan.text.R
import com.farakhvan.text.data.AppDatabase
import com.farakhvan.text.data.CampaignStatus
import com.farakhvan.text.data.MemberEntity
import com.farakhvan.text.data.Recipient
import com.farakhvan.text.data.Repo
import com.farakhvan.text.data.Settings
import com.farakhvan.text.sending.SendController
import com.farakhvan.text.util.ContactsHelper
import com.farakhvan.text.util.PhoneNormalizer
import com.farakhvan.text.util.SmsUtil
import com.farakhvan.text.util.hasPermission
import com.farakhvan.text.util.ltr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onOpenCampaign: (Long) -> Unit,
    onCreateGroup: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val settings = remember { Settings.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // ---- data sources
    val groupsState by remember { db.groupDao().observeAll() }.collectLoad()
    val groups = (groupsState as? Load.Ready)?.value.orEmpty()
    val selectedGroup = groups.firstOrNull { it.group.id == Draft.groupId }?.group

    var groupMembers by remember { mutableStateOf<List<MemberEntity>>(emptyList()) }
    LaunchedEffect(Draft.groupId) {
        if (Draft.groupId >= 0) {
            db.memberDao().observe(Draft.groupId).collect { groupMembers = it }
        } else {
            groupMembers = emptyList()
        }
    }

    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { names = ContactsHelper.nameMap(context) }

    // ---- recipients for the chosen target
    val contactSnapshot = Draft.contacts.toList()
    val built = remember(Draft.mode, Draft.pasted, groupMembers, names, contactSnapshot) {
        when (Draft.mode) {
            0 -> Pair(
                groupMembers.map {
                    Recipient(it.number, it.name.ifBlank { names[it.number].orEmpty() }, it.valid)
                },
                0
            )

            1 -> {
                val parsed = PhoneNormalizer.parseBulk(Draft.pasted)
                Pair(
                    parsed.numbers.map { Recipient(it.canonical, names[it.canonical].orEmpty(), it.valid) },
                    parsed.duplicates
                )
            }

            else -> {
                val map = LinkedHashMap<String, Recipient>()
                var dup = 0
                for (c in contactSnapshot) {
                    val p = PhoneNormalizer.normalize(c.number)
                    if (map.containsKey(p.canonical)) dup++ else map[p.canonical] = Recipient(p.canonical, c.name, p.valid)
                }
                Pair(map.values.toList(), dup)
            }
        }
    }
    val recipients: List<Recipient> = built.first
    val duplicates: Int = built.second
    val validRecipients = recipients.filter { it.valid }
    val invalidRecipients = recipients.filter { !it.valid }

    // ---- SMS counter
    val longestName = validRecipients.maxByOrNull { it.name.length }?.name.orEmpty()
    val previewText = Draft.message.replace("{name}", longestName)
    val info = remember(previewText) { SmsUtil.analyze(previewText) }

    // ---- SIM selection
    var phonePermission by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.READ_PHONE_STATE))
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        phonePermission = it
    }
    val sims = remember(phonePermission) { SmsUtil.activeSims(context) }
    val looksDual = remember { SmsUtil.looksDualSim(context) }
    val simSlot = if (Draft.simSlot >= 0) Draft.simSlot else settings.defaultSim

    // ---- sending flow
    var showSmsRationale by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }

    val busyMessage = stringResource(R.string.send_busy)
    val pastedLabel = stringResource(R.string.target_pasted_label)
    val contactsLabel = stringResource(R.string.target_contacts_label)

    fun startSending() {
        scope.launch {
            if (validRecipients.isEmpty()) return@launch
            val subId = if (sims.size >= 2) sims.firstOrNull { it.slot == simSlot }?.subId ?: -1 else -1
            val target = when (Draft.mode) {
                0 -> selectedGroup?.name.orEmpty()
                1 -> pastedLabel
                else -> contactsLabel
            }
            val campaignId = Repo.createCampaign(
                context = context,
                groupId = if (Draft.mode == 0) selectedGroup?.id else null,
                targetName = target,
                message = Draft.message,
                recipients = recipients,
                simSubId = subId,
                delayMs = settings.delaySec * 1000L
            )
            if (!SendController.start(context, campaignId)) {
                // Could not start: keep it resumable instead of leaving a ghost "running" campaign.
                db.campaignDao().setStatus(campaignId, CampaignStatus.INTERRUPTED)
            }
            Draft.message = ""
            onOpenCampaign(campaignId)
        }
    }

    fun proceed() {
        if (settings.confirmBeforeSend) showConfirm = true else startSending()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (context.hasPermission(Manifest.permission.SEND_SMS)) proceed() else showDenied = true
    }

    fun onSendClick() {
        if (SendController.active.value != null) {
            scope.launch { snackbar.showSnackbar(busyMessage) }
            return
        }
        if (context.hasPermission(Manifest.permission.SEND_SMS)) proceed() else showSmsRationale = true
    }

    // ---- UI
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_new_sms)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSendClick() },
                    enabled = validRecipients.isNotEmpty() && Draft.message.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(60.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.send_button, validRecipients.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target selector
            val modeLabels = listOf(
                R.string.target_group,
                R.string.target_pasted,
                R.string.target_contacts
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modeLabels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = Draft.mode == index,
                        onClick = { Draft.mode = index },
                        shape = SegmentedButtonDefaults.itemShape(index, modeLabels.size)
                    ) { Text(stringResource(label)) }
                }
            }

            when (Draft.mode) {
                0 -> {
                    when (groupsState) {
                        is Load.Loading -> LoadingRow()
                        is Load.Failed -> ErrorBox(Modifier.height(160.dp))
                        is Load.Ready -> {
                            if (groups.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(stringResource(R.string.compose_no_groups))
                                        TextButton(onClick = onCreateGroup) {
                                            Text(stringResource(R.string.compose_create_group))
                                        }
                                    }
                                }
                            } else {
                                Column {
                                    OutlinedButton(
                                        onClick = { groupMenu = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                    ) {
                                        Text(
                                            selectedGroup?.let {
                                                (it.emoji.ifBlank { "" } + " " + it.name).trim()
                                            } ?: stringResource(R.string.group_select_hint),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                                        groups.forEach { item ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        item.group.name + "  (" + item.memberCount + ")"
                                                    )
                                                },
                                                onClick = {
                                                    Draft.groupId = item.group.id
                                                    groupMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> OutlinedTextField(
                    value = Draft.pasted,
                    onValueChange = { Draft.pasted = it },
                    minLines = 4,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.paste_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                else -> Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showPicker = true }) {
                        Icon(Icons.Filled.Contacts, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.contacts_pick_button))
                    }
                    if (Draft.contacts.isNotEmpty()) {
                        Text(
                            stringResource(R.string.contacts_selected, Draft.contacts.size),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { Draft.contacts.clear() }) {
                            Text(stringResource(R.string.contacts_clear))
                        }
                    }
                }
            }

            // Recipient summary (valid / invalid / duplicates)
            if (recipients.isNotEmpty() || duplicates > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(
                                R.string.parse_summary,
                                validRecipients.size,
                                invalidRecipients.size,
                                duplicates
                            ),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (invalidRecipients.isNotEmpty()) {
                            Text(
                                stringResource(R.string.invalid_skipped_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            invalidRecipients.take(5).forEach {
                                Text(
                                    it.number.ltr(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (invalidRecipients.size > 5) {
                                Text(
                                    "… +" + (invalidRecipients.size - 5),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Message + counter
            OutlinedTextField(
                value = Draft.message,
                onValueChange = { Draft.message = it },
                minLines = 5,
                maxLines = 10,
                label = { Text(stringResource(R.string.message_label)) },
                supportingText = {
                    Column {
                        if (info.parts == 0) {
                            Text(stringResource(R.string.sms_counter_empty))
                        } else {
                            Text(
                                stringResource(
                                    R.string.sms_counter,
                                    stringResource(if (info.unicode) R.string.enc_unicode else R.string.enc_gsm),
                                    info.used,
                                    info.perPart,
                                    info.parts
                                )
                            )
                            if (validRecipients.isNotEmpty()) {
                                Text(
                                    stringResource(
                                        R.string.sms_total_cost,
                                        info.parts * validRecipients.size
                                    )
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { Draft.message = Draft.message + "{name}" },
                    label = { Text(stringResource(R.string.token_chip)) }
                )
                Text(
                    stringResource(R.string.token_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // SIM row
            if (sims.size >= 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sim_title), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        sims.forEachIndexed { index, sim ->
                            SegmentedButton(
                                selected = simSlot == sim.slot,
                                onClick = { Draft.simSlot = sim.slot },
                                shape = SegmentedButtonDefaults.itemShape(index, sims.size)
                            ) {
                                Text(stringResource(R.string.sim_slot, sim.slot + 1, sim.label))
                            }
                        }
                    }
                }
            } else if (looksDual && !phonePermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sim_permission_explain))
                        TextButton(onClick = {
                            phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }) { Text(stringResource(R.string.sim_permission_allow)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- dialogs & sheets
    if (showPicker) {
        ContactPickerDialog(
            onDismiss = { showPicker = false },
            onPicked = { picked ->
                showPicker = false
                val existing = Draft.contacts.map { it.number }.toSet()
                Draft.contacts.addAll(picked.filter { it.number !in existing })
            }
        )
    }

    if (showSmsRationale) {
        RationaleDialog(
            title = stringResource(R.string.perm_sms_title),
            text = stringResource(R.string.perm_sms_explain),
            onDismiss = { showSmsRationale = false },
            onContinue = {
                showSmsRationale = false
                val perms = mutableListOf(Manifest.permission.SEND_SMS)
                if (Build.VERSION.SDK_INT >= 33 &&
                    !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(perms.toTypedArray())
            }
        )
    }

    if (showDenied) {
        AlertDialogSimple(
            title = stringResource(R.string.perm_sms_denied_title),
            text = stringResource(R.string.perm_sms_denied_text),
            onDismiss = { showDenied = false }
        )
    }

    if (showConfirm) {
        val first = validRecipients.firstOrNull()
        val totalParts = remember(validRecipients, Draft.message) {
            validRecipients.sumOf { SmsUtil.analyze(Draft.message.replace("{name}", it.name)).parts }
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showConfirm = false },
            sheetState = sheetState
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.confirm_recipients, validRecipients.size),
                    style = MaterialTheme.typography.titleMedium
                )
                if (invalidRecipients.isNotEmpty()) {
                    Text(
                        stringResource(R.string.confirm_skipped, invalidRecipients.size),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    stringResource(R.string.confirm_first_numbers),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    validRecipients.take(4).joinToString("\n") { it.number.ltr() } +
                        if (validRecipients.size > 4) "\n…" else ""
                )
                HorizontalDivider()
                Text(stringResource(R.string.confirm_message), style = MaterialTheme.typography.labelLarge)
                Text(Draft.message.replace("{name}", first?.name.orEmpty()))
                HorizontalDivider()
                Text(
                    stringResource(R.string.confirm_cost, validRecipients.size, totalParts),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showConfirm = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = {
                            showConfirm = false
                            startSending()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text(stringResource(R.string.confirm_send)) }
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun AlertDialogSimple(title: String, text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    )
}
