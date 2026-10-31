package com.autodroid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsScreen(activity: MainActivity, generation: Int, onRefresh: () -> Unit) {
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            activity.getString(R.string.execution_logs),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            activity.getString(R.string.logs_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var clear by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onRefresh) { Text(activity.getString(R.string.refresh)) }
            TextButton(onClick = { clear = true }) { Text(activity.getString(R.string.clear_logs)) }
        }
        if (clear)
            AlertDialog(
                onDismissRequest = { clear = false },
                title = { Text(activity.getString(R.string.clear_logs_title)) },
                text = { Text(activity.getString(R.string.clear_logs_description)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Store.prefs(activity).edit().remove("logs").apply()
                            clear = false
                            onRefresh()
                        }
                    ) {
                        Text(activity.getString(R.string.clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clear = false }) {
                        Text(activity.getString(R.string.cancel))
                    }
                },
            )
        val logs =
            remember(generation) {
                Store.prefs(activity).getString("logs", activity.getString(R.string.logs_empty))
                    ?: ""
            }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f).padding(bottom = 16.dp),
        ) {
            SelectionContainer {
                Text(
                    logs,
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
