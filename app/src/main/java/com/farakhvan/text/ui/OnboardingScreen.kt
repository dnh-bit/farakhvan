package com.farakhvan.text.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farakhvan.text.R

private enum class GuideTone { WARNING, INFO, LOCK }

private data class GuidePage(
    val titleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector,
    val tone: GuideTone
)

private val GuidePages = listOf(
    GuidePage(R.string.guide_page1_title, R.string.guide_page1_body, Icons.Filled.Warning, GuideTone.WARNING),
    GuidePage(R.string.guide_page2_title, R.string.guide_page2_body, Icons.Filled.Info, GuideTone.INFO),
    GuidePage(R.string.guide_page3_title, R.string.guide_page3_body, Icons.Filled.Lock, GuideTone.LOCK)
)

/**
 * First-run guide: carrier limits (why bulk SMS gets a SIM blocked), Unicode/SMS-part cost,
 * and what the app does with the phone book — plus an offer to switch the app lock on.
 *
 * Also reachable from Settings at any time. "رد کردن" leaves the whole guide in one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var page by rememberSaveable { mutableIntStateOf(0) }

    val current = GuidePages[page.coerceIn(0, GuidePages.lastIndex)]
    val isLast = page == GuidePages.lastIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_header)) },
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.guide_skip),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            PageDots(page = page, count = GuidePages.size)
            Spacer(Modifier.height(14.dp))

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    IconBadge(icon = current.icon, tone = current.tone)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(current.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(current.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(18.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
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
                        if (!isLast) page += 1 else onFinish()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (!isLast) R.string.guide_next else R.string.guide_start
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Three pills instead of "۲ از ۳": the position reads at a glance without parsing a number. */
@Composable
private fun PageDots(page: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == page
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(8.dp)
                    .width(if (selected) 26.dp else 8.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tone: GuideTone) {
    val container: Color
    val content: Color
    when (tone) {
        GuideTone.WARNING -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }

        GuideTone.INFO -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }

        GuideTone.LOCK -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(color = container, shape = RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
    }
}
