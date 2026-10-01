package com.revosleap.text.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revosleap.text.R
import com.revosleap.text.data.Group
import com.revosleap.text.data.GroupSummary
import com.revosleap.text.data.Numbers
import com.revosleap.text.data.Recipient

@Composable
fun GroupEditor(initial: Group? = null, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: 0xFF00796B) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_group else R.string.rename_group)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emoji, { emoji = it.take(12) }, label = { Text(stringResource(R.string.group_emoji)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xFF00796B, 0xFF16317D, 0xFF721B3E).forEachIndexed { index, swatch ->
                        FilterChip(selected = color == swatch, onClick = { color = swatch },
                            label = { Text(stringResource(R.string.color_choice, index + 1), color = Color(swatch)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), emoji, color) }) {
            Text(stringResource(R.string.save))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(model: MainModel, snackbar: SnackbarHostState, open: (Long) -> Unit) {
    val groups by remember { model.dao.groups() }.collectAsStateWithLifecycle(initialValue = null)
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GroupSummary?>(null) }
    val deleteNotice = stringResource(R.string.group_deleted)
    val undo = stringResource(R.string.undo)
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.new_group)) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_groups)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (groups == null) CircularProgressIndicator()
            else if (groups!!.isEmpty()) EmptyState(R.string.first_group, R.string.groups_hint)
            else {
                val filtered = groups!!.filter { it.name.contains(query, true) }
                if (filtered.isEmpty()) EmptyState(R.string.no_search_results)
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { group ->
                        val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) deleting = group
                            false
                        })
                        SwipeToDismissBox(state = swipe, backgroundContent = {
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete_group),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }) {
                            ListItem(modifier = Modifier.clickable { open(group.id) },
                                headlineContent = { Text(group.name) },
                                supportingContent = {
                                    Column {
                                        Text(stringResource(R.string.member_count, group.count))
                                        if (group.lastUsed > 0) Text(stringResource(R.string.last_used, formattedDate(group.lastUsed)))
                                    }
                                },
                                leadingContent = {
                                    Surface(color = Color(group.color), shape = MaterialTheme.shapes.medium) {
                                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                            if (group.emoji.isNotBlank()) Text(group.emoji)
                                            else Icon(Icons.Default.Groups, null, tint = Color.White)
                                        }
                                    }
                                },
                                trailingContent = { IconButton(onClick = { deleting = group }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete_group))
                                } })
                        }
                    }
                }
            }
        }
    }
    if (creating) GroupEditor(onDismiss = { creating = false }) { name, emoji, color ->
        creating = false
        model.run { val id = model.dao.insertGroup(Group(name = name, emoji = emoji, color = color)); open(id) }
    }
    deleting?.let { group ->
        ConfirmDialog(R.string.delete_group,
            stringResource(R.string.delete_group_question, group.name, group.count),
            onDismiss = { deleting = null }, onConfirm = {
                deleting = null
                model.run {
                    model.dao.hideGroup(group.id, true)
                    val result = snackbar.showSnackbar(deleteNotice, actionLabel = undo, withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) model.dao.hideGroup(group.id, false)
                    else model.dao.deleteGroup(group.id)
                }
            })
    }
}

@Composable
fun GroupScreen(id: Long, model: MainModel, gate: PermissionGate, back: () -> Unit) {
    val context = LocalContext.current
    val group by remember(id) { model.dao.group(id) }
        .collectAsStateWithLifecycle(initialValue = Group(id = -1, name = ""))
    val members by remember(id) { model.dao.members(id) }.collectAsStateWithLifecycle(initialValue = null)
    var selected by rememberSaveable(id) { mutableStateOf(listOf<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteGroup by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<List<Recipient>?>(null) }
    var loadingPicker by remember { mutableStateOf(false) }
    var pickerTitle by remember { mutableStateOf(R.string.pick_contacts) }
    val add: (List<Recipient>) -> Unit = { input ->
        model.run {
            val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val result = model.repository.addMembers(id, model.enrich(input, canRead))
            model.notify(R.string.added_summary, result.first, result.second)
        }
    }
    if (group == null) {
        Column {
            EmptyState(R.string.group_missing)
            TextButton(onClick = back) { Text(stringResource(R.string.back)) }
        }
        return
    }
    if (group?.id == -1L || members == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Scaffold(floatingActionButton = {
        Box {
            FloatingActionButton(onClick = { menu = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_members)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.pick_contacts)) }, onClick = {
                    menu = false
                    gate(arrayOf(Manifest.permission.READ_CONTACTS), R.string.permission_contacts) {
                        loadingPicker = true
                        model.run {
                            try { pickerTitle = R.string.pick_contacts; picker = model.repository.contacts() }
                            finally { loadingPicker = false }
                        }
                    }
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_number)) }, onClick = { menu = false; adding = "single" })
                DropdownMenuItem(text = { Text(stringResource(R.string.paste_numbers)) }, onClick = { menu = false; adding = "paste" })
                DropdownMenuItem(text = { Text(stringResource(R.string.copy_from_group)) }, onClick = {
                    menu = false
                    loadingPicker = true
                    model.run {
                        try {
                            pickerTitle = R.string.copy_from_group
                            picker = model.dao.allMembers().filter { it.groupId != id }
                                .map { Recipient(it.number, it.name, it.valid) }.distinctBy { it.number }
                        } finally { loadingPicker = false }
                    }
                })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (members == null || loadingPicker) CircularProgressIndicator()
            group?.let { current ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(current.emoji + " " + current.name, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.member_unique, members.orEmpty().size,
                            members.orEmpty().map { it.number }.distinct().size))
                    }
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, stringResource(R.string.rename_group)) }
                    IconButton(onClick = { deleteGroup = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_group)) }
                }
            }
            if (selected.isNotEmpty()) TextButton(onClick = { deleting = true }) {
                Text(stringResource(R.string.delete_selected, selected.size), color = MaterialTheme.colorScheme.error)
            }
            if (members?.isEmpty() == true) EmptyState(R.string.empty_group, R.string.add_members_hint)
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(members.orEmpty(), key = { it.id }) { member ->
                    val checked = member.id in selected
                    ListItem(
                        modifier = Modifier.clickable {
                            selected = if (checked) selected - member.id else selected + member.id
                        },
                        headlineContent = { PhoneText(member.number, !member.valid) },
                        supportingContent = {
                            if (member.name.isNotBlank()) Text(member.name)
                            if (!member.valid) Text(stringResource(R.string.invalid_number), color = MaterialTheme.colorScheme.error)
                        }, leadingContent = { Checkbox(checked, onCheckedChange = null) })
                }
            }
        }
    }
    adding?.let { mode ->
        AddNumbersDialog(mode == "paste", onDismiss = { adding = null }) { rows -> adding = null; add(rows) }
    }
    picker?.let { rows -> RecipientPicker(pickerTitle, rows, onDismiss = { picker = null }) { input -> picker = null; add(input) } }
    if (renaming && group != null) GroupEditor(group, onDismiss = { renaming = false }) { name, emoji, color ->
        renaming = false
        model.run { model.dao.renameGroup(id, name, emoji, color) }
    }
    if (deleting) ConfirmDialog(R.string.delete_members, stringResource(R.string.delete_members_question, selected.size),
        onDismiss = { deleting = false }, onConfirm = {
            val ids = selected
            deleting = false
            model.run { model.dao.deleteMembers(id, ids); selected = emptyList() }
        })
    if (deleteGroup) ConfirmDialog(R.string.delete_group,
        stringResource(R.string.delete_group_question, group?.name.orEmpty(), members.orEmpty().size),
        onDismiss = { deleteGroup = false }, onConfirm = {
            deleteGroup = false
            model.run { model.dao.deleteGroup(id); back() }
        })
}

@Composable
fun AddNumbersDialog(bulk: Boolean, onDismiss: () -> Unit, onSave: (List<Recipient>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = if (bulk) Numbers.parse(input) else {
        val number = Numbers.normalize(input)
        if (input.isBlank()) emptyList() else listOf(Recipient(number.number, valid = number.valid))
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (bulk) R.string.paste_numbers else R.string.add_number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, label = { Text(stringResource(R.string.phone_numbers)) },
                    singleLine = !bulk, minLines = if (bulk) 5 else 1,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp))
                Text(stringResource(if (bulk) R.string.paste_hint else R.string.number_hint))
                if (parsed.isNotEmpty()) Text(stringResource(R.string.input_summary, parsed.size, parsed.count { !it.valid }),
                    color = if (parsed.any { !it.valid }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = input.isNotBlank(), onClick = { onSave(parsed) }) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
