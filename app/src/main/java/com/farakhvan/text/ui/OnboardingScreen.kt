package com.farakhvan.text.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.farakhvan.text.R

private data class GuidePage(val titleRes: Int, val bodyRes: Int, val icon: ImageVector)

private val GuidePages = listOf(
    GuidePage(R.string.guide_page1_title, R.string.guide_page1_body, Icons.Filled.Warning),
    GuidePage(R.string.guide_page2_title, R.string.guide_page2_body, Icons.Filled.Info),
    GuidePage(R.string.guide_page3_title, R.string.guide_page3_body, Icons.Filled.Lock)
)

/**
 * First-run guide: carrier limits (why bulk SMS gets a SIM blocked), Unicode/SMS-part cost,
 * and what the app does with the phone book. Also reachable from Settings at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current = GuidePages[page.coerceIn(0, GuidePages.lastIndex)]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_step, page + 1, GuidePages.size)) },
                actions = {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Icon(
                imageVector = current.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(current.titleRes),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(current.bodyRes),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    TextButton(onClick = { page -= 1 }) {
                        Text(stringResource(R.string.guide_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (page < GuidePages.lastIndex) page += 1 else onDone()
                    }
                ) {
                    Text(
                        stringResource(
                            if (page < GuidePages.lastIndex) R.string.guide_next else R.string.guide_start
                        )
                    )
                }
            }
        }
    }
}
