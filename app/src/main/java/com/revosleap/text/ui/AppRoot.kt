package com.revosleap.text.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.revosleap.text.R
import com.revosleap.text.data.Settings

private object Routes {
    const val GROUPS = "groups"
    const val COMPOSE = "compose"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val GROUP = "group/{id}"
    const val CAMPAIGN = "campaign/{id}"
    fun group(id: Long) = "group/$id"
    fun campaign(id: Long) = "campaign/$id"
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun AppRoot(openCampaignId: Long, onOpenCampaignHandled: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }

    FarakhvanTheme(themeMode = settings.theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route

        val tabs = listOf(
            Tab(Routes.GROUPS, R.string.tab_groups, Icons.Filled.Groups),
            Tab(Routes.COMPOSE, R.string.tab_new_sms, Icons.AutoMirrored.Filled.Send),
            Tab(Routes.HISTORY, R.string.tab_history, Icons.Filled.History)
        )

        LaunchedEffect(openCampaignId) {
            if (openCampaignId >= 0) {
                nav.navigate(Routes.campaign(openCampaignId)) { launchSingleTop = true }
                onOpenCampaignHandled()
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (tabs.any { it.route == route }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { navigateTab(nav, tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.GROUPS,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                composable(Routes.GROUPS) {
                    GroupsScreen(
                        onOpenGroup = { nav.navigate(Routes.group(it)) },
                        onSettings = { nav.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.COMPOSE) {
                    ComposeScreen(
                        onOpenCampaign = { id ->
                            nav.navigate(Routes.campaign(id)) {
                                popUpTo(Routes.COMPOSE) { inclusive = false }
                            }
                        },
                        onCreateGroup = { navigateTab(nav, Routes.GROUPS) },
                        onSettings = { nav.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onOpenCampaign = { nav.navigate(Routes.campaign(it)) },
                        onSettings = { nav.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
                composable(
                    Routes.GROUP,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStack ->
                    GroupDetailScreen(
                        groupId = backStack.arguments?.getLong("id") ?: -1L,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(
                    Routes.CAMPAIGN,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStack ->
                    CampaignScreen(
                        campaignId = backStack.arguments?.getLong("id") ?: -1L,
                        onBack = { nav.popBackStack() }
                    )
                }
            }
        }
    }
}

private fun navigateTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
