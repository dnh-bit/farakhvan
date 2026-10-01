package com.revosleap.text.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.revosleap.text.R
import kotlinx.coroutines.flow.collectLatest

data class SimChoice(val id: Int, val slot: Int)
class PermissionRequest(val names: Array<String>, val explanation: Int, val granted: () -> Unit)
typealias PermissionGate = (Array<String>, Int, () -> Unit) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarakhvanRoot(openedCampaign: Long, model: MainModel = viewModel()) {
    val context = LocalContext.current
    val settings by model.preferences.settings.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val startupError by model.startupError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "groups"
    val topLevel = route in listOf("groups", "compose", "history")
    var permission by remember { mutableStateOf<PermissionRequest?>(null) }
    var awaiting by remember { mutableStateOf<PermissionRequest?>(null) }
    var sims by remember { mutableStateOf<List<SimChoice>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = awaiting
        awaiting = null
        if (request != null) {
            val mandatory = request.names.filter { name -> name != Manifest.permission.POST_NOTIFICATIONS }
            if (mandatory.all { name ->
                ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
            }) request.granted()
            else model.notify(R.string.error_permission)
        }
    }
    val gate: PermissionGate = { names, explanation, granted ->
        val applicable = names.filter { it != Manifest.permission.POST_NOTIFICATIONS || Build.VERSION.SDK_INT >= 33 }
        if (applicable.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) granted()
        else permission = PermissionRequest(applicable.toTypedArray(), explanation, granted)
    }
    val loadSims: () -> Unit = {
        gate(arrayOf(Manifest.permission.READ_PHONE_STATE), R.string.permission_sim) {
            try {
                sims = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                    .orEmpty().sortedBy { it.simSlotIndex }.map { SimChoice(it.subscriptionId, it.simSlotIndex + 1) }
                if (sims.isNotEmpty() && settings.sim == -1) {
                    model.preferences.update(settings.copy(sim = sims.first().id))
                }
                if (sims.isEmpty()) model.notify(R.string.no_sim)
            } catch (_: Exception) { model.notify(R.string.error_permission) }
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            loadSims()
        }
    }
    LaunchedEffect(model) { model.messages.collectLatest { snackbar.showSnackbar(it) } }
    LaunchedEffect(openedCampaign, ready) {
        if (openedCampaign > 0 && ready) nav.navigate("campaign/$openedCampaign") { launchSingleTop = true }
    }
    FarakhvanTheme(settings.theme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text(stringResource(when (route) {
                        "compose" -> R.string.new_sms
                        "history" -> R.string.history
                        "settings" -> R.string.settings
                        "group/{id}" -> R.string.group_detail
                        "campaign/{id}" -> R.string.campaign_detail
                        else -> R.string.app_name
                    })) }, navigationIcon = {
                        if (!topLevel) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }, actions = {
                        if (ready && route != "settings") IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
                    })
                },
                bottomBar = {
                    if (topLevel && ready) NavigationBar {
                        listOf(Triple("groups", R.string.groups, Icons.Default.Groups),
                            Triple("compose", R.string.new_sms, Icons.AutoMirrored.Filled.Send),
                            Triple("history", R.string.history, Icons.Default.History)).forEach { (path, title, icon) ->
                            NavigationBarItem(selected = route == path, onClick = {
                                nav.navigate(path) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }, icon = { Icon(icon, stringResource(title)) }, label = { Text(stringResource(title)) })
                        }
                    }
                }, snackbarHost = { SnackbarHost(snackbar) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (startupError) EmptyState(R.string.error_database)
                    else if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else NavHost(nav, startDestination = "groups") {
                        composable("groups") { GroupsScreen(model, snackbar) { nav.navigate("group/$it") } }
                        composable("group/{id}") { e ->
                            GroupScreen(e.arguments?.getString("id")?.toLongOrNull() ?: 0, model, gate) { nav.popBackStack() }
                        }
                        composable("compose") {
                            ComposeScreen(model, gate, sims, loadSims) { nav.navigate("campaign/$it") }
                        }
                        composable("history") { HistoryScreen(model) { nav.navigate("campaign/$it") } }
                        composable("campaign/{id}") { e ->
                            CampaignScreen(e.arguments?.getString("id")?.toLongOrNull() ?: 0, model, gate)
                        }
                        composable("settings") { SettingsScreen(model, sims, loadSims) }
                    }
                }
            }
            permission?.let { request ->
                AlertDialog(onDismissRequest = { permission = null },
                    title = { Text(stringResource(R.string.permission_title)) },
                    text = { Text(stringResource(request.explanation)) },
                    confirmButton = { TextButton(onClick = {
                        awaiting = request
                        permission = null
                        launcher.launch(request.names)
                    }) { Text(stringResource(R.string.continue_action)) } },
                    dismissButton = { TextButton(onClick = { permission = null }) { Text(stringResource(R.string.cancel)) } })
            }
        }
    }
}
