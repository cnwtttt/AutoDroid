package com.autodroid

import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Compose owns presentation; SMS monitoring and execution remain in the service. */
class ComposeHost(private val activity: MainActivity) {
    private var revision by mutableIntStateOf(0)

    fun refresh() {
        revision++
    }

    fun install() {
        activity.setContent {
            val dark = isSystemInDarkTheme()
            var colorStyle by rememberSaveable { mutableStateOf(ColorStyle.read(activity)) }
            val colors =
                if (colorStyle == ColorStyle.X) {
                    if (dark) XDarkColors else XLightColors
                } else {
                    if (dark) PlayDarkColors else PlayLightColors
                }
            MaterialTheme(colorScheme = colors) {
                App(revision, colorStyle) { style ->
                    colorStyle = style
                    Store.prefs(activity)
                        .edit()
                        .putString("colorStyle", style.preferenceValue)
                        .apply()
                    activity.composeThemeChanged()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App(generation: Int, colorStyle: ColorStyle, onColorStyle: (ColorStyle) -> Unit) {
        var page by rememberSaveable { mutableIntStateOf(0) }
        var editor by rememberSaveable { mutableIntStateOf(-2) }
        var deleteIndex by remember { mutableIntStateOf(-1) }
        val rules = remember(generation) { Store.rules(activity) }
        val enabled = remember(generation) { Store.prefs(activity).getBoolean("monitor", false) }
        val navLabels =
            listOf(
                activity.getString(R.string.nav_automation),
                activity.getString(R.string.nav_logs),
                activity.getString(R.string.nav_settings),
            )
        val navIcons =
            listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.List, Icons.Default.Settings)
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            activity.getString(R.string.app_name),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        SuggestionChip(
                            onClick = { page = 2 },
                            label = { Text(activity.getString(R.string.local_only_badge)) },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    navLabels.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = { page = index },
                            icon = { Icon(navIcons[index], contentDescription = null) },
                            label = { Text(title) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (page == 0)
                    ExtendedFloatingActionButton(
                        onClick = { editor = -1 },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text(activity.getString(R.string.create_macro)) },
                    )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                when (page) {
                    0 ->
                        AutomationScreen(
                            activity,
                            rules,
                            enabled,
                            onCreate = { editor = -1 },
                            onEdit = { editor = it },
                            onDelete = { deleteIndex = it },
                            onRefresh = ::refresh,
                        )
                    1 -> LogsScreen(activity, generation, ::refresh)
                    else -> SettingsScreen(activity, colorStyle, onColorStyle)
                }
            }
        }
        if (editor >= -1) {
            val index = editor
            key(index) {
                MacroEditor(
                    activity,
                    if (index < 0) JSONObject() else rules.optJSONObject(index) ?: JSONObject(),
                    index,
                    generation,
                    onDismiss = {
                        editor = -2
                        activity.composeContactsClosed()
                    },
                    onSave = { rule ->
                        val latest = Store.rules(activity)
                        if (index < 0) latest.put(rule) else latest.put(index, rule)
                        Store.save(activity, latest)
                        editor = -2
                        activity.composeContactsClosed()
                        refresh()
                    },
                )
            }
        }
        if (deleteIndex >= 0)
            AlertDialog(
                onDismissRequest = { deleteIndex = -1 },
                title = { Text(activity.getString(R.string.delete_macro_title)) },
                text = { Text(activity.getString(R.string.delete_macro_description)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            rules.remove(deleteIndex)
                            Store.save(activity, rules)
                            deleteIndex = -1
                            refresh()
                        }
                    ) {
                        Text(
                            activity.getString(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteIndex = -1 }) {
                        Text(activity.getString(R.string.cancel))
                    }
                },
            )
    }
}
