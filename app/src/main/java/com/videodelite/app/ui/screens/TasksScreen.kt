package com.videodelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videodelite.app.AppGraph
import com.videodelite.app.R
import com.videodelite.app.core.Format
import com.videodelite.app.media.TaskState
import com.videodelite.app.ui.components.SectionCard

@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    val tasks by AppGraph.taskManager.tasks.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.nav_tasks), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            val finished = tasks.count { it.state == TaskState.DONE || it.state == TaskState.FAILED || it.state == TaskState.CANCELED }
            if (finished > 0) {
                TextButton(onClick = { AppGraph.taskManager.clearFinished() }) {
                    Text(stringResource(R.string.tasks_clear_done))
                }
            }
        }

        if (tasks.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text(
                stringResource(R.string.tasks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        } else {
            LazyColumn {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: com.videodelite.app.media.CompressionTask) {
    SectionCard {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                val stateLabel = when (task.state) {
                    TaskState.QUEUED -> stringResource(R.string.task_state_queued)
                    TaskState.ANALYZING -> stringResource(R.string.task_state_analyzing)
                    TaskState.COMPRESSING -> stringResource(R.string.task_state_compressing, task.progress)
                    TaskState.DONE -> stringResource(R.string.task_state_done)
                    TaskState.FAILED -> stringResource(R.string.task_state_failed)
                    TaskState.CANCELED -> stringResource(R.string.task_state_canceled)
                }
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (task.state) {
                        TaskState.DONE -> MaterialTheme.colorScheme.primary
                        TaskState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            when (task.state) {
                TaskState.COMPRESSING -> {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TaskState.DONE -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.task_size_result,
                            Format.bytes(task.sourceSizeBytes),
                            Format.bytes(task.outputSizeBytes),
                            Format.percentSaved(task.sourceSizeBytes, task.outputSizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.task_saved_to),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TaskState.FAILED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        task.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {}
            }

            if (task.state == TaskState.QUEUED || task.state == TaskState.ANALYZING || task.state == TaskState.COMPRESSING) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { AppGraph.taskManager.cancel(task.id) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.task_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
