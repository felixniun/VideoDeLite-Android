package com.videodelite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videodelite.app.AppGraph
import com.videodelite.app.BuildConfig
import com.videodelite.app.R
import com.videodelite.app.data.AppSettings
import com.videodelite.app.media.Quality
import com.videodelite.app.media.VideoCodec
import com.videodelite.app.ui.components.SectionCard
import com.videodelite.app.ui.components.SectionLabel
import com.videodelite.app.ui.components.VdSegmented
import com.videodelite.app.ui.components.uiMessage
import kotlinx.coroutines.launch

private fun isNewerVersion(remote: String, current: String): Boolean {
    fun parse(v: String) = v.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    val r = parse(remote)
    val c = parse(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val rv = r.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (rv != cv) return rv > cv
    }
    return false
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onGoAccount: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val accountState by AppGraph.account.state.collectAsStateWithLifecycle()
    val hevcAvailable = remember { AppGraph.engine.hasHevcEncoder() }

    // (latest version, isNewer) or error message; strings resolved in composable context.
    var updateResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)

        SectionLabel(stringResource(R.string.settings_appearance))
        SectionCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                VdSegmented(
                    options = listOf("", "light", "dark"),
                    selected = settings.theme,
                    label = {
                        when (it) {
                            "light" -> stringResource(R.string.theme_light)
                            "dark" -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        }
                    },
                ) { picked -> scope.launch { AppGraph.settings.setTheme(picked) } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                VdSegmented(
                    options = listOf("", "zh-CN", "en"),
                    selected = settings.language,
                    label = {
                        when (it) {
                            "zh-CN" -> stringResource(R.string.lang_zh)
                            "en" -> stringResource(R.string.lang_en)
                            else -> stringResource(R.string.lang_system)
                        }
                    },
                ) { picked -> scope.launch { AppGraph.settings.setLanguage(picked) } }
            }
        }

        SectionLabel(stringResource(R.string.settings_compression))
        SectionCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.settings_default_codec), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                VdSegmented(
                    options = VideoCodec.entries.toList(),
                    selected = VideoCodec.entries.firstOrNull { it.id == settings.defaultCodec } ?: VideoCodec.H264,
                    label = { it.name },
                    enabled = { it != VideoCodec.H265 || hevcAvailable },
                ) { picked ->
                    scope.launch { AppGraph.settings.setDefaultCodec(picked.id) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.settings_default_quality), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                VdSegmented(
                    options = Quality.entries.toList(),
                    selected = Quality.entries.firstOrNull { it.id == settings.defaultQuality } ?: Quality.MID,
                    label = {
                        when (it) {
                            Quality.LOW -> stringResource(R.string.quality_low)
                            Quality.MID -> stringResource(R.string.quality_mid)
                            Quality.HIGH -> stringResource(R.string.quality_high)
                        }
                    },
                ) { picked -> scope.launch { AppGraph.settings.setDefaultQuality(picked.id) } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.settings_parallel), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                VdSegmented(
                    options = listOf(1, 2, 3),
                    selected = settings.parallelTasks,
                    label = { "$it" },
                ) { picked ->
                    scope.launch {
                        AppGraph.settings.setParallelTasks(picked)
                        AppGraph.taskManager.setParallelism(picked)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.settings_output_note), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_output_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_account))
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoAccount() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (accountState.loggedIn) accountState.username
                        else stringResource(R.string.settings_account_hint_login),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_about))
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val v = AppGraph.account.checkUpdate()
                                updateResult = v.latest to isNewerVersion(v.latest, BuildConfig.VERSION_NAME)
                            } catch (e: Exception) {
                                updateError = e.uiMessage(context)
                            } finally {
                                busy = false
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    stringResource(R.string.settings_update),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.version_name, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text(
                stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    updateResult?.let { (latest, isNewer) ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = {
                Text(if (isNewer) stringResource(R.string.update_new, latest) else stringResource(R.string.update_latest))
            },
            text = if (isNewer) {
                { Text(stringResource(R.string.update_new_hint)) }
            } else null,
            confirmButton = {
                TextButton(onClick = { updateResult = null }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
    updateError?.let { message ->
        AlertDialog(
            onDismissRequest = { updateError = null },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = { updateError = null }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}
