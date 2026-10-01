package com.revosleap.text.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.revosleap.text.R
import com.revosleap.text.data.AddReport
import com.revosleap.text.data.AppDatabase
import com.revosleap.text.data.GroupEntity
import com.revosleap.text.data.MemberEntity
import com.revosleap.text.data.NewMember
import com.revosleap.text.data.Repo
import com.revosleap.text.util.PhoneNormalizer
import com.revosleap.text.util.PhoneNumber
import com.revosleap.text.util.ltr
import kotlinx.coroutines.launch

private enum class GroupDialog { RENAME, DELETE_GROUP, DELETE_MEMBERS, ADD_NUMBER, PASTE, COPY, PICK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(groupId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val groupState by remember(groupId) { db.groupDao().observe(groupId) }.collectLoad()
    val memberState by remember(groupId) { db.memberDao().observe(groupId) }.collectLoad()

    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var dialog by remember { mutableStateOf<GroupDialog?>(null) }
    var fabMenu by remember { mutableStateOf(false) }

    val group: GroupEntity? = (groupState as? Load.Ready)?.value
    val groupLoaded = groupState is Load.Ready

    // The group was deleted (here or elsewhere): leave the screen.
    LaunchedEffect(groupLoaded, group) {
        if (groupLoaded && group == null) onBack()
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    fun report(r: AddReport) {
        scope.launch {
            snackbar.showSnackbar(
                context.getString(R.string.add_report, r.added, r.duplicates, r.invalid)
            )
        }
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val members = (memberState as? Load.Ready)?.value.orEmpty()
                            selected = members.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { dialog = GroupDialog.DELETE_MEMBERS }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(group?.name.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { dialog = GroupDialog.RENAME }, enabled = group != null) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.group_rename_title))
                        }
                        IconButton(onClick = { dialog = GroupDialog.DELETE_GROUP }, enabled = group != null) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.group_delete_title))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selected.isEmpty() && group != null) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { fabMenu = true },
                        icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        text = { Text(stringResource(R.string.members_add)) }
                    )
                    DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_pick_contacts)) },
                            leadingIcon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                            onClick = { fabMenu = false; dialog = GroupDialog.PICK }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_number)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { fabMenu = false; dialog = GroupDialog.ADD_NUMBER }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_paste_list)) },
                            leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                            onClick = { fabMenu = false; dialog = GroupDialog.PASTE }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_from_group)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { fabMenu = false; dialog = GroupDialog.COPY }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val s = memberState) {
                is Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorBox()
                is Load.Ready -> {
                    val members = s.value
                    val unique = members.map { it.number }.distinct().size
                    val invalid = members.count { !it.valid }
                    Text(
                        text = buildString {
                            append(stringResource(R.string.group_header, members.size, unique))
                            if (invalid > 0) {
                                append(" · ")
                                append(stringResource(R.string.group_header_invalid, invalid))
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                    if (members.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.GroupAdd,
                            title = stringResource(R.string.members_empty_title),
                            subtitle = stringResource(R.string.members_empty_subtitle)
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
                        ) {
                            items(members, key = { it.id }) { m ->
                                val checked = m.id in selected
                                val errorColor = MaterialTheme.colorScheme.error
                                ListItem(
                                    modifier = Modifier.clickable {
                                        selected = if (checked) selected - m.id else selected + m.id
                                    },
                                    leadingContent = {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                    },
                                    headlineContent = {
                                        Text(
                                            text = if (m.name.isNotBlank()) m.name else m.number.ltr(),
                                            color = if (m.valid) MaterialTheme.colorScheme.onSurface else errorColor
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = if (m.valid) {
                                                m.number.ltr()
                                            } else {
                                                m.number.ltr() + "  ·  " + stringResource(R.string.number_invalid)
                                            },
                                            color = if (m.valid) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                errorColor
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- dialogs
    when (dialog) {
        GroupDialog.RENAME -> if (group != null) {
            GroupEditDialog(
                initial = group,
                onDismiss = { dialog = null },
                onConfirm = { name, emoji, color ->
                    dialog = null
                    scope.launch { db.groupDao().update(group.copy(name = name, emoji = emoji, color = color)) }
                }
            )
        }

        GroupDialog.DELETE_GROUP -> if (group != null) {
            ConfirmDialog(
                title = stringResource(R.string.group_delete_title),
                text = stringResource(R.string.group_delete_confirm, group.name),
                confirmLabel = stringResource(R.string.action_delete),
                destructive = true,
                onDismiss = { dialog = null },
                onConfirm = {
                    dialog = null
                    scope.launch { db.groupDao().delete(group) }
                }
            )
        }

        GroupDialog.DELETE_MEMBERS -> ConfirmDialog(
            title = stringResource(R.string.members_delete_title),
            text = stringResource(R.string.members_delete_confirm, selected.size),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                val ids = selected.toList()
                dialog = null
                selected = emptySet()
                scope.launch { db.memberDao().deleteByIds(ids) }
            }
        )

        GroupDialog.ADD_NUMBER -> AddNumberDialog(
            onDismiss = { dialog = null },
            onConfirm = { text ->
                dialog = null
                scope.launch {
                    val phone = PhoneNormalizer.normalize(text)
                    report(Repo.addMembers(context, groupId, listOf(NewMember(phone))))
                }
            }
        )

        GroupDialog.PASTE -> PasteListDialog(
            onDismiss = { dialog = null },
            onConfirm = { text ->
                dialog = null
                scope.launch { report(Repo.addFromText(context, groupId, text)) }
            }
        )

        GroupDialog.COPY -> CopyFromGroupDialog(
            currentGroupId = groupId,
            onDismiss = { dialog = null },
            onCopy = { members ->
                dialog = null
                scope.launch {
                    val entries = members.map {
                        NewMember(PhoneNumber(it.number, it.valid, it.number), it.name)
                    }
                    report(Repo.addMembers(context, groupId, entries))
                }
            }
        )

        GroupDialog.PICK -> ContactPickerDialog(
            onDismiss = { dialog = null },
            onPicked = { contacts ->
                dialog = null
                scope.launch {
                    val entries = contacts.map { NewMember(PhoneNormalizer.normalize(it.number), it.name) }
                    report(Repo.addMembers(context, groupId, entries))
                }
            }
        )

        null -> Unit
    }
}

@Composable
private fun AddNumberDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { PhoneNormalizer.normalize(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.number_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank()) {
                    Text(
                        text = if (parsed.valid) {
                            stringResource(R.string.number_will_be_saved, parsed.canonical.ltr())
                        } else {
                            stringResource(R.string.number_invalid_warning)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (parsed.valid) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun PasteListDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { PhoneNormalizer.parseBulk(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_paste_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 5,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.paste_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.parse_summary,
                            parsed.validCount,
                            parsed.invalidCount,
                            parsed.duplicates
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed.numbers.isNotEmpty(), onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Step 1: choose another group. Step 2: tick the saved numbers to copy into this group. */
@Composable
private fun CopyFromGroupDialog(
    currentGroupId: Long,
    onDismiss: () -> Unit,
    onCopy: (List<MemberEntity>) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val groupsState by remember { db.groupDao().observeAll() }.collectLoad()
    var source by remember { mutableStateOf<GroupEntity?>(null) }
    var members by remember { mutableStateOf<List<MemberEntity>?>(null) }
    var picked by remember { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(source) {
        val s = source
        if (s != null) {
            val list = db.memberDao().getAll(s.id)
            members = list
            picked = list.map { it.id }.toSet()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(source?.name ?: stringResource(R.string.add_from_group))
        },
        text = {
            Box(Modifier.heightIn(max = 360.dp)) {
                val src = source
                if (src == null) {
                    when (val g = groupsState) {
                        is Load.Loading -> LoadingBox()
                        is Load.Failed -> ErrorBox()
                        is Load.Ready -> {
                            val others = g.value.filter { it.group.id != currentGroupId }
                            if (others.isEmpty()) {
                                Text(stringResource(R.string.copy_no_other_groups))
                            } else {
                                LazyColumn {
                                    items(others, key = { it.group.id }) { item ->
                                        ListItem(
                                            modifier = Modifier.clickable { source = item.group },
                                            leadingContent = { GroupAvatar(item.group, 36.dp) },
                                            headlineContent = { Text(item.group.name) },
                                            supportingContent = {
                                                Text(stringResource(R.string.group_member_count, item.memberCount))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val list = members
                    if (list == null) {
                        LoadingBox()
                    } else if (list.isEmpty()) {
                        Text(stringResource(R.string.members_empty_title))
                    } else {
                        LazyColumn {
                            items(list, key = { it.id }) { m ->
                                val checked = m.id in picked
                                ListItem(
                                    modifier = Modifier.clickable {
                                        picked = if (checked) picked - m.id else picked + m.id
                                    },
                                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                                    headlineContent = {
                                        Text(if (m.name.isNotBlank()) m.name else m.number.ltr())
                                    },
                                    supportingContent = { Text(m.number.ltr()) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (source != null) {
                TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = { onCopy(members.orEmpty().filter { it.id in picked }) }
                ) { Text(stringResource(R.string.action_copy_count, picked.size)) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (source != null) {
                    source = null
                    members = null
                } else {
                    onDismiss()
                }
            }) {
                Text(
                    stringResource(if (source != null) R.string.action_back else R.string.action_cancel)
                )
            }
        }
    )
}
