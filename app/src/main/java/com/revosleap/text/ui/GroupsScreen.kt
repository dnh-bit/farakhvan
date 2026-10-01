package com.revosleap.text.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.revosleap.text.R
import com.revosleap.text.data.AppDatabase
import com.revosleap.text.data.GroupEntity
import com.revosleap.text.data.GroupWithCount
import com.revosleap.text.util.Formatting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onOpenGroup: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state by remember { db.groupDao().observeAll() }.collectLoad()
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    val deletedMsg = stringResource(R.string.group_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    fun deleteWithUndo(item: GroupWithCount) {
        scope.launch {
            val members = db.memberDao().getAll(item.group.id)
            db.groupDao().delete(item.group)
            val result = snackbar.showSnackbar(
                message = deletedMsg,
                actionLabel = undoLabel,
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                db.groupDao().insert(item.group)
                db.memberDao().restoreAll(members)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_groups)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.group_new_fab)) }
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
                    val all = s.value
                    if (all.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Groups,
                            title = stringResource(R.string.groups_empty_title),
                            subtitle = stringResource(R.string.groups_empty_subtitle),
                            actionLabel = stringResource(R.string.group_new_fab),
                            onAction = { showCreate = true }
                        )
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text(stringResource(R.string.search)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        val filtered = all.filter { it.group.name.contains(query.trim(), ignoreCase = true) }
                        if (filtered.isEmpty()) {
                            EmptyState(
                                icon = Icons.Filled.Search,
                                title = stringResource(R.string.search_no_result)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.group.id }) { item ->
                                    GroupRow(
                                        item = item,
                                        onClick = { onOpenGroup(item.group.id) },
                                        onDelete = { deleteWithUndo(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        GroupEditDialog(
            initial = null,
            onDismiss = { showCreate = false },
            onConfirm = { name, emoji, color ->
                showCreate = false
                scope.launch {
                    val id = db.groupDao().insert(
                        GroupEntity(
                            name = name,
                            emoji = emoji,
                            color = color,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    onOpenGroup(id)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupRow(item: GroupWithCount, onClick: () -> Unit, onDelete: () -> Unit) {
    val currentItem by rememberUpdatedState(item)
    val onDeleteState by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                if (currentItem.group.id >= 0) onDeleteState()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.elevatedCardColors()
        ) {
            val lastUsed = if (item.group.lastUsedAt > 0) {
                stringResource(R.string.group_last_used, Formatting.dateTime(item.group.lastUsedAt))
            } else {
                stringResource(R.string.group_never_used)
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                leadingContent = { GroupAvatar(item.group) },
                headlineContent = { Text(item.group.name, style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.group_member_count, item.memberCount))
                        Text(
                            lastUsed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}
