package com.videodelite.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videodelite.app.AppGraph
import com.videodelite.app.R
import com.videodelite.app.media.TaskState
import com.videodelite.app.ui.screens.AccountScreen
import com.videodelite.app.ui.screens.HistoryScreen
import com.videodelite.app.ui.screens.HomeScreen
import com.videodelite.app.ui.screens.SettingsScreen
import com.videodelite.app.ui.screens.TasksScreen

private data class Tab(
    val id: String,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun AppRoot() {
    var current by rememberSaveable { mutableStateOf("home") }
    val tabs = listOf(
        Tab("home", R.string.nav_home, Icons.Rounded.Home),
        Tab("tasks", R.string.nav_tasks, Icons.Rounded.Bolt),
        Tab("history", R.string.nav_history, Icons.Rounded.Schedule),
        Tab("settings", R.string.nav_settings, Icons.Rounded.Settings),
        Tab("account", R.string.nav_account, Icons.Rounded.Person),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val tasks by AppGraph.taskManager.tasks.collectAsStateWithLifecycle()
            val activeCount = tasks.count {
                it.state == TaskState.QUEUED || it.state == TaskState.ANALYZING || it.state == TaskState.COMPRESSING
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.id,
                        onClick = { current = tab.id },
                        icon = {
                            if (tab.id == "tasks" && activeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$activeCount") } }) {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            "tasks" -> TasksScreen(modifier)
            "history" -> HistoryScreen(modifier)
            "settings" -> SettingsScreen(modifier, onGoAccount = { current = "account" })
            "account" -> AccountScreen(modifier)
            else -> HomeScreen(modifier, onStarted = { current = "tasks" }, onGoAccount = { current = "account" })
        }
    }
}
