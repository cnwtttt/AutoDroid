package com.autodroid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(
    activity: MainActivity,
    colorStyle: ColorStyle,
    onColorStyle: (ColorStyle) -> Unit,
) {
    Column(
        Modifier.widthIn(max = 680.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            activity.getString(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            activity.getString(R.string.settings_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingCard(
            activity.getString(R.string.appearance_title),
            activity.getString(R.string.appearance_description),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = colorStyle == ColorStyle.X,
                    onClick = { onColorStyle(ColorStyle.X) },
                    label = { Text(activity.getString(R.string.theme_x)) },
                )
                FilterChip(
                    selected = colorStyle == ColorStyle.PLAY,
                    onClick = { onColorStyle(ColorStyle.PLAY) },
                    label = { Text(activity.getString(R.string.theme_play)) },
                )
            }
        }
        SettingCard(
            activity.getString(R.string.permissions_title),
            activity.getString(R.string.permissions_description),
        ) {
            TextButton(onClick = activity::composePermissions) {
                Text(activity.getString(R.string.manage_permissions))
            }
        }
        SettingCard(
            activity.getString(R.string.backup_title),
            activity.getString(R.string.backup_description),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { activity.composeBackup(true) }) {
                    Text(activity.getString(R.string.export_macros))
                }
                TextButton(onClick = { activity.composeBackup(false) }) {
                    Text(activity.getString(R.string.restore_macros))
                }
            }
        }
        SettingCard(
            activity.getString(R.string.diagnostics_title),
            activity.getString(R.string.diagnostics_description),
        ) {
            TextButton(onClick = activity::composeRecent) {
                Text(activity.getString(R.string.read_recent_messages))
            }
        }
        Text(
            activity.getString(R.string.privacy_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            activity.getString(R.string.privacy_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            activity.getString(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
