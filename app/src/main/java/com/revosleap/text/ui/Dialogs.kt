package com.revosleap.text.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.revosleap.text.R
import com.revosleap.text.data.GroupEntity
import com.revosleap.text.util.ContactItem
import com.revosleap.text.util.ContactsHelper
import com.revosleap.text.util.PhoneNormalizer
import com.revosleap.text.util.hasPermission
import com.revosleap.text.util.ltr

val GroupColors: List<Int> = listOf(
    0xFF00897B, 0xFF3949AB, 0xFFD81B60, 0xFFF4511E, 0xFF7CB342, 0xFF8E24AA
).map { it.toInt() }

@Composable
fun GroupAvatar(group: GroupEntity, size: Dp = 44.dp) {
    val color = if (group.color != 0) Color(group.color) else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = group.emoji.ifBlank { group.name.take(1) },
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

/** Create / rename dialog: name, optional emoji, optional color. */
@Composable
fun GroupEditDialog(
    initial: GroupEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, color: Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji.orEmpty()) }
    var color by remember {
        mutableIntStateOf(initial?.color?.takeIf { it != 0 } ?: GroupColors[0])
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.group_new_title else R.string.group_rename_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text(stringResource(R.string.group_emoji_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.group_color),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupColors.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (selected) Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), emoji.trim(), color) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Short human explanation shown BEFORE the system permission prompt. */
@Composable
fun RationaleDialog(
    title: String,
    text: String,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
        }
    )
}

/** Full-screen multi-select contact picker (replaces the old MultiContactPicker library). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onPicked: (List<ContactItem>) -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.READ_CONTACTS))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    var contacts by remember { mutableStateOf<List<ContactItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(granted) {
        if (granted) {
            try {
                contacts = ContactsHelper.load(context)
            } catch (e: Exception) {
                failed = true
                contacts = emptyList()
            }
        }
    }

    val visible = remember(contacts, query) {
        val list = contacts.orEmpty()
        val q = PhoneNormalizer.toLatinDigits(query).trim()
        if (q.isEmpty()) list else list.filter {
            it.name.contains(q, ignoreCase = true) || it.number.replace(" ", "").contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.contacts_pick_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        if (granted && visible.isNotEmpty()) {
                            IconButton(onClick = {
                                val allSelected = visible.all { it.number in selected }
                                if (allSelected) {
                                    selected.removeAll(visible.map { it.number }.toSet())
                                } else {
                                    visible.forEach { if (it.number !in selected) selected.add(it.number) }
                                }
                            }) {
                                Icon(
                                    Icons.Filled.SelectAll,
                                    contentDescription = stringResource(R.string.action_select_all)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (granted) {
                    Box(Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                val chosen = contacts.orEmpty().filter { it.number in selected }
                                onPicked(chosen)
                            },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(stringResource(R.string.contacts_add_selected, selected.size))
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when {
                    !granted -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Contacts,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.perm_contacts_explain),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) }) {
                                Text(stringResource(R.string.perm_allow))
                            }
                        }
                    }

                    contacts == null -> LoadingBox()

                    failed -> ErrorBox()

                    contacts.orEmpty().isEmpty() -> EmptyState(
                        icon = Icons.Filled.Contacts,
                        title = stringResource(R.string.contacts_empty)
                    )

                    else -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.search)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (visible.isEmpty()) {
                            EmptyState(
                                icon = Icons.Filled.Contacts,
                                title = stringResource(R.string.search_no_result)
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(visible, key = { it.number }) { c ->
                                    val checked = c.number in selected
                                    ListItem(
                                        modifier = Modifier.clickable {
                                            if (checked) selected.remove(c.number) else selected.add(c.number)
                                        },
                                        headlineContent = {
                                            Text(c.name.ifBlank { stringResource(R.string.no_name) })
                                        },
                                        supportingContent = { Text(c.number.ltr()) },
                                        trailingContent = {
                                            Checkbox(checked = checked, onCheckedChange = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
