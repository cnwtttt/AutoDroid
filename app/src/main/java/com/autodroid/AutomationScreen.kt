package com.autodroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutomationScreen(
    activity: MainActivity,
    rules: JSONArray,
    enabled: Boolean,
    onCreate: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        Modifier.widthIn(max = AppDimensions.size680).fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = AppDimensions.size20,
                end = AppDimensions.size20,
                top = AppDimensions.size8,
                bottom = AppDimensions.size100,
            ),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.size16),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.size8)) {
                Text(
                    activity.getString(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    activity.getString(R.string.home_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                border =
                    BorderStroke(AppDimensions.size1, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    Modifier.padding(AppDimensions.size24),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.size12),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimensions.size12),
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            null,
                            tint =
                                if (enabled) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (enabled) activity.getString(R.string.monitor_enabled)
                            else activity.getString(R.string.monitor_paused),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text(
                        if (enabled) activity.getString(R.string.monitor_enabled_description)
                        else activity.getString(R.string.monitor_paused_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = activity::composeToggle, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (enabled) activity.getString(R.string.pause_monitor)
                            else activity.getString(R.string.start_monitor)
                        )
                    }
                }
            }
        }
        val needsPermission =
            activity.checkSelfPermission(Manifest.permission.READ_SMS) !=
                PackageManager.PERMISSION_GRANTED ||
                !Settings.canDrawOverlays(activity) ||
                (Build.VERSION.SDK_INT >= 33 &&
                    activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED)
        if (needsPermission)
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(AppDimensions.size20),
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.size8),
                    ) {
                        Text(
                            activity.getString(R.string.complete_permissions),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            activity.getString(R.string.complete_permissions_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = activity::composePermissions) {
                            Text(activity.getString(R.string.check_permissions))
                        }
                    }
                }
            }
        item {
            val count =
                (0 until rules.length()).count {
                    rules.optJSONObject(it)?.optBoolean("enabled", true) == true
                }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    activity.getString(R.string.my_macros),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    activity.getString(R.string.enabled_macro_count, count, rules.length()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rules.length() == 0)
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(AppDimensions.size28),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.size12),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            null,
                            Modifier.size(AppDimensions.size40),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            activity.getString(R.string.empty_macros_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            activity.getString(R.string.empty_macros_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = { onCreate() }) {
                            Text(activity.getString(R.string.create_sms_macro))
                        }
                    }
                }
            }
        itemsIndexed(
            (0 until rules.length()).mapNotNull {
                rules.optJSONObject(it)?.let { rule -> it to rule }
            }
        ) { _, pair ->
            val (index, rule) = pair
            MacroCard(
                rule,
                onToggle = { checked ->
                    rule.put("enabled", checked)
                    Store.save(activity, rules)
                    onRefresh()
                },
                onTest = { activity.composeTest(rule) },
                onEdit = { onEdit(index) },
                onDelete = { onDelete(index) },
            )
        }
        item {
            OutlinedButton(onClick = activity::composeStop, modifier = Modifier.fillMaxWidth()) {
                Text(activity.getString(R.string.stop_current_alert))
            }
        }
    }
}
