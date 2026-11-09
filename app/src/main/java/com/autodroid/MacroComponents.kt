package com.autodroid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.json.JSONObject

@Composable
internal fun MacroCard(
    rule: JSONObject,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val activity = androidx.compose.ui.platform.LocalContext.current
    val channels = androidx.compose.ui.res.stringArrayResource(R.array.audio_channels)
    var more by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(AppDimensions.size1, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(AppDimensions.size20),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.size8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.optString("name"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                Switch(
                    checked = rule.optBoolean("enabled", true),
                    onCheckedChange = onToggle,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                activity.getString(
                                    R.string.enable_macro_accessibility,
                                    rule.optString("name"),
                                )
                        },
                )
            }
            Text(
                activity.getString(
                    R.string.sender_summary,
                    Store.numbers(rule).joinToString(activity.getString(R.string.list_separator)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (rule.optString("keyword").isEmpty())
                    activity.getString(R.string.match_any_content)
                else
                    activity.getString(
                        R.string.keyword_summary_multi,
                        activity.getString(
                            if (rule.optString("keywordMode") == "all") R.string.keyword_all
                            else R.string.keyword_any
                        ),
                        rule.optString("keyword").replace("\n", " / "),
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    activity.getString(
                        R.string.audio_summary,
                        channels[rule.optInt("channel").coerceIn(0, 3)],
                        rule.optInt("volume"),
                        rule.optInt("duration"),
                    ),
                    Modifier.padding(
                        horizontal = AppDimensions.size12,
                        vertical = AppDimensions.size8,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.size8),
            ) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(AppDimensions.size18))
                    Spacer(Modifier.width(AppDimensions.size6))
                    Text(activity.getString(R.string.test))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text(activity.getString(R.string.edit)) }
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(Icons.Default.MoreVert, activity.getString(R.string.more_actions))
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text(activity.getString(R.string.delete_macro)) },
                            onClick = {
                                more = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingCard(
    title: String,
    subtitle: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppDimensions.size20),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.size8),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            actions()
        }
    }
}
