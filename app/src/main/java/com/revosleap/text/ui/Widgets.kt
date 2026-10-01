package com.revosleap.text.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.revosleap.text.R
import com.revosleap.text.data.Recipient
import com.revosleap.text.data.SendResult
import com.revosleap.text.data.States
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmptyState(title: Int, subtitle: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun PhoneText(number: String, invalid: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(number, color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ConfirmDialog(title: Int, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun RecipientPicker(title: Int, recipients: List<Recipient>, onDismiss: () -> Unit,
    onSelected: (List<Recipient>) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (recipients.isEmpty()) Text(stringResource(R.string.no_numbers))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(recipients.filter { it.name.contains(search, true) || it.number.contains(search) },
                        key = { it.number }) { row ->
                        val checked = row.number in selected
                        Row(Modifier.fillMaxWidth().clickable {
                            selected = if (checked) selected - row.number else selected + row.number
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = null)
                            Column {
                                if (row.name.isNotBlank()) Text(row.name)
                                PhoneText(row.number, !row.valid)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = selected.isNotEmpty(), onClick = {
            onSelected(recipients.filter { it.number in selected })
        }) { Text(stringResource(R.string.add_selected, selected.size)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun statusText(status: String): String = stringResource(when (status) {
    States.PENDING -> R.string.status_pending
    States.RUNNING -> R.string.status_running
    States.SENDING -> R.string.status_sending
    States.SENT -> R.string.status_sent
    States.FAILED -> R.string.status_failed
    States.PAUSED -> R.string.status_paused
    States.CANCELLED -> R.string.status_cancelled
    States.INTERRUPTED -> R.string.status_interrupted
    States.UNKNOWN -> R.string.status_unknown
    States.COMPLETE -> R.string.status_complete
    else -> R.string.status_pending
})

@Composable
fun reasonText(row: SendResult): String = when (row.reason) {
    "" -> ""
    "invalid" -> stringResource(R.string.invalid_number)
    "empty" -> stringResource(R.string.empty_message)
    "permission" -> stringResource(R.string.error_permission)
    "timeout", "uncertain" -> stringResource(R.string.uncertain_result)
    "partial" -> stringResource(R.string.partial_result)
    "sms:1" -> stringResource(R.string.sms_generic)
    "sms:2" -> stringResource(R.string.sms_radio_off)
    "sms:3" -> stringResource(R.string.sms_null_pdu)
    "sms:4" -> stringResource(R.string.sms_no_service)
    "sms:5" -> stringResource(R.string.sms_limit)
    "sms:6", "sms:7", "sms:8" -> stringResource(R.string.sms_policy)
    else -> stringResource(R.string.sms_other, row.reason.substringAfter("sms:", ""))
}

fun formattedDate(time: Long): String =
    if (time == 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("fa")).format(Date(time))
