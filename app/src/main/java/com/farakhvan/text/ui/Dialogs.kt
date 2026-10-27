package com.farakhvan.text.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import com.farakhvan.text.R
import com.farakhvan.text.data.GroupEntity
import com.farakhvan.text.util.ContactItem
import com.farakhvan.text.util.ContactsHelper
import com.farakhvan.text.util.PhoneNormalizer
import com.farakhvan.text.util.hasPermission
import com.farakhvan.text.util.ltr

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

/** Curated offline emoji set — no keyboard, no font download, identical on every device. */
private val GroupEmojis: List<String> = listOf(
    "📣", "📢", "👥", "👨‍👩‍👧", "🏠", "🏪", "🛒", "📚",
    "🎓", "🕌", "🏥", "🚗", "🚌", "✈️", "⚽", "🎯",
    "🔥", "⭐", "✅", "📌", "📞", "💬", "🎁", "💡",
    "💰", "❤️", "🌹", "🌷", "🍀", "☕", "🍕", "🎵",
    "📷", "🛠️", "💼", "👷", "🚚", "🧾", "📊", "📅",
    "⏰", "🔔", "😀", "😎", "🙏", "👍", "✨", "🎉"
)

@Composable
private fun EmojiChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

/** Create / rename dialog: name, emoji picked in-app (no keyboard), optional color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupEditDialog(
    initial: GroupEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, color: Int) -> Unit
) {
    val initialName = initial?.name.orEmpty()
    val initialEmoji = initial?.emoji.orEmpty()
    val initialColor = initial?.color?.takeIf { it != 0 } ?: GroupColors[0]

    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var color by remember { mutableIntStateOf(initialColor) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = name != initialName || emoji != initialEmoji || color != initialColor

    fun requestClose() {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    BackHandler(enabled = dirty && !confirmDiscard) { confirmDiscard = true }

    AlertDialog(
        onDismissRequest = { requestClose() },
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.group_new_title else R.string.group_rename_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.group_emoji_optional),
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (emoji.isNotBlank()) {
                        TextButton(
                            onClick = { emoji = "" },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(stringResource(R.string.contacts_clear))
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GroupEmojis.forEach { e ->
                            EmojiChoice(
                                text = e,
                                selected = e == emoji,
                                onClick = { emoji = if (emoji == e) "" else e }
                            )
                        }
                    }
                }
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
            TextButton(onClick = { requestClose() }) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_group_text),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
            destructive = true
        )
    }
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

/** Full-screen multi-select contact picker (replaces the old MultiContactPicker library).
 *
 *  Keyboard safety: the dialog window does not fit system windows, the whole scaffold gets
 *  imePadding(), and the submit button lives in a bottomBar Surface — so it stays visible
 *  above the keyboard instead of hiding behind it. Going back with a non-empty selection
 *  always asks for confirmation first.
 */
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
    var confirmDiscard by remember { mutableStateOf(false) }

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

    fun submit() {
        val chosen = contacts.orEmpty().filter { it.number in selected }
        if (chosen.isNotEmpty()) onPicked(chosen)
    }

    fun requestClose() {
        if (selected.isNotEmpty()) confirmDiscard = true else onDismiss()
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) { requestClose() }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (selected.isEmpty()) stringResource(R.string.contacts_pick_title)
                            else stringResource(R.string.contacts_selected, selected.size)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { requestClose() }) {
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
                        if (selected.isNotEmpty()) {
                            TextButton(onClick = { submit() }) {
                                Text(stringResource(R.string.contacts_submit, selected.size))
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (granted) {
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Button(
                                onClick = { submit() },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text(
                                    if (selected.isEmpty()) stringResource(R.string.contacts_none_selected)
                                    else stringResource(R.string.contacts_add_selected, selected.size)
                                )
                            }
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
                            LazyColumn(Modifier.weight(1f)) {
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

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_contacts_text, selected.size),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
            destructive = true
        )
    }
}
