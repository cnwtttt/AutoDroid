package com.autodroid

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MacroEditor(
    activity: MainActivity,
    old: JSONObject,
    index: Int,
    revision: Int,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    val channels = androidx.compose.ui.res.stringArrayResource(R.array.audio_channels)
    var name by rememberSaveable {
        mutableStateOf(old.optString("name", activity.getString(R.string.default_macro_name)))
    }
    var numbers by rememberSaveable { mutableStateOf(Store.numberText(old)) }
    var keyword by rememberSaveable { mutableStateOf(old.optString("keyword")) }
    var channel by rememberSaveable { mutableIntStateOf(old.optInt("channel").coerceIn(0, 3)) }
    var volume by rememberSaveable { mutableFloatStateOf(old.optInt("volume", 80).toFloat()) }
    var duration by rememberSaveable { mutableStateOf(old.optInt("duration", 60).toString()) }
    var message by rememberSaveable {
        mutableStateOf(old.optString("message", activity.getString(R.string.default_alert_message)))
    }
    var error by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var sound by rememberSaveable { mutableStateOf(old.optString("sound", "")) }
    DisposableEffect(Unit) {
        activity.composeSoundValue(sound)
        onDispose {}
    }
    LaunchedEffect(revision) { sound = activity.composeSoundValue() }
    val contactTarget = remember { EditText(activity) }
    DisposableEffect(contactTarget) {
        val watcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    numbers = s.toString()
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        contactTarget.addTextChangedListener(watcher)
        onDispose {
            contactTarget.removeTextChangedListener(watcher)
            activity.composeContactsClosed()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(0.94f).imePadding(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, activity.getString(R.string.cancel_editing))
                    }
                    Text(
                        if (index < 0) activity.getString(R.string.new_sms_macro)
                        else activity.getString(R.string.edit_sms_macro),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(
                        onClick = {
                            val parsed = Store.parseNumbers(numbers)
                            val seconds = duration.toIntOrNull() ?: 0
                            error =
                                when {
                                    name.isBlank() ->
                                        activity.getString(R.string.error_macro_name_required)
                                    parsed.isEmpty() ->
                                        activity.getString(R.string.error_sender_required)
                                    seconds !in 5..600 ->
                                        activity.getString(R.string.error_duration_range)
                                    else -> ""
                                }
                            if (error.isEmpty())
                                onSave(
                                    JSONObject()
                                        .put("source", "sms")
                                        .put("name", name.trim())
                                        .put("numbers", JSONArray(parsed))
                                        .put("keyword", keyword)
                                        .put("channel", channel)
                                        .put("volume", volume.toInt())
                                        .put("duration", seconds)
                                        .put("message", message)
                                        .put("sound", activity.composeSoundValue())
                                        .put("enabled", old.optBoolean("enabled", true))
                                )
                        }
                    ) {
                        Text(activity.getString(R.string.save))
                    }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text(activity.getString(R.string.macro_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        activity.getString(R.string.trigger_conditions),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        numbers,
                        { numbers = it },
                        label = { Text(activity.getString(R.string.sender_numbers)) },
                        supportingText = { Text(activity.getString(R.string.sender_numbers_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            contactTarget.setText(numbers)
                            activity.composeContacts(contactTarget)
                        }
                    ) {
                        Text(activity.getString(R.string.choose_contacts))
                    }
                    OutlinedTextField(
                        keyword,
                        { keyword = it },
                        label = { Text(activity.getString(R.string.keyword)) },
                        supportingText = { Text(activity.getString(R.string.keyword_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        activity.getString(R.string.sound_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            channels[channel],
                            {},
                            readOnly = true,
                            label = { Text(activity.getString(R.string.audio_channel)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier =
                                Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            channels.forEachIndexed { i, title ->
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = {
                                        channel = i
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        activity.getString(R.string.volume_percent, volume.toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..100f,
                        steps = 99,
                    )
                    OutlinedTextField(
                        duration,
                        { duration = it },
                        label = { Text(activity.getString(R.string.duration_seconds)) },
                        supportingText = { Text(activity.getString(R.string.duration_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (sound.isEmpty()) activity.getString(R.string.default_sound_label)
                        else activity.getString(R.string.custom_sound_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { activity.composeSound(false) }) {
                            Text(activity.getString(R.string.system_ringtone))
                        }
                        OutlinedButton(onClick = { activity.composeSound(true) }) {
                            Text(activity.getString(R.string.local_audio))
                        }
                    }
                    Text(
                        activity.getString(R.string.alert_content),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        message,
                        { message = it },
                        label = { Text(activity.getString(R.string.alert_message)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        activity.getString(R.string.test_volume_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
