package com.autodroid;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.util.*;

public class MainActivity extends androidx.activity.ComponentActivity {
    private DialogUi ui;
    private ComposeHost composeHost;
    private boolean readingRecentSms;
    private EditText contactTarget;
    private String selectedSound = "";

    @Override
    public void onCreate(Bundle state) {
        composeThemeChanged();
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            this::backgroundFromBack);
        }
        JSONArray rules = Store.rules(this);
        if (Store.migrateToSms(rules)) {
            Store.save(this, rules);
            Store.log(this, getString(R.string.log_legacy_rules_migrated));
        }
        Store.prefs(this)
                .edit()
                .putBoolean("senderDiagnostics", false)
                .putBoolean("notificationFieldDiagnostics", false)
                .putBoolean("notificationFieldValues", false)
                .apply();
        render();
    }

    @Override
    public void onBackPressed() {
        backgroundFromBack();
    }

    private void backgroundFromBack() {
        boolean moved = moveTaskToBack(true);
        Store.log(
                this,
                "APP_BACK_BACKGROUND moved="
                        + moved
                        + " monitor="
                        + Store.prefs(this).getBoolean("monitor", false));
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
        Store.log(
                this,
                "APP_RESUME version="
                        + BuildConfig.VERSION_NAME
                        + " monitor="
                        + Store.prefs(this).getBoolean("monitor", false)
                        + " readSmsPermission="
                        + granted(Manifest.permission.READ_SMS)
                        + " receiveSmsPermission="
                        + granted(Manifest.permission.RECEIVE_SMS)
                        + " overlayPermission="
                        + Settings.canDrawOverlays(this));
        for (ActivityManager.AppTask task : getSystemService(ActivityManager.class).getAppTasks())
            task.setExcludeFromRecents(true);
        if (Store.prefs(this).getBoolean("monitor", false) && !MacroService.isRunning) {
            try {
                Store.log(this, getString(R.string.log_monitor_restore));
                startForegroundService(new Intent(this, MacroService.class));
            } catch (RuntimeException e) {
                Store.log(this, "MONITOR_RESTORE_FAILED " + e.getMessage());
            }
        }
    }

    @Override
    public void onStop() {
        Store.log(
                this,
                "APP_ACTIVITY_STOP monitor="
                        + Store.prefs(this).getBoolean("monitor", false)
                        + " serviceRunning="
                        + MacroService.isRunning);
        super.onStop();
    }

    private LinearLayout column() {
        return ui.column(20);
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView t = ui.label(value, size, size >= 20 ? ui.ink : ui.muted, size >= 20);
        t.setPadding(0, ui.dp(6), 0, ui.dp(6));
        parent.addView(t);
        return t;
    }

    private void button(LinearLayout parent, String label, Runnable action) {
        Button b = ui.button(label, false, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = ui.dp(8);
        parent.addView(b, lp);
    }

    private boolean needsSms() {
        JSONArray rules = Store.rules(this);
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r != null
                    && r.optBoolean("enabled", true)
                    && !"notification".equals(r.optString("source"))) return true;
        }
        return false;
    }

    private boolean granted(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void render() {
        if (composeHost == null) {
            composeHost = new ComposeHost(this);
            composeHost.install();
        } else composeHost.refresh();
    }

    void composeToggle() {
        toggleMonitor(Store.prefs(this).getBoolean("monitor", false));
    }

    void composeStop() {
        startService(new Intent(this, MacroService.class).setAction("SILENCE"));
    }

    void composePermissions() {
        permissions();
    }

    void composeRecent() {
        recentSmsDiagnostics();
    }

    void composeTest(JSONObject rule) {
        testRule(rule);
    }

    void composeBackup(boolean export) {
        if (export)
            launch(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(
                                    Intent.EXTRA_TITLE,
                                    "AutoDroid-macros-"
                                            + new java.text.SimpleDateFormat(
                                                            "yyyyMMdd-HHmmss", Locale.ROOT)
                                                    .format(new Date())
                                            + ".json"),
                    30);
        else
            launch(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*"),
                    31);
    }

    void composeSound(boolean file) {
        if (file)
            launch(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("audio/*")
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                    11);
        else
            launch(
                    new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true),
                    10);
    }

    String composeSoundValue() {
        return selectedSound;
    }

    void composeSoundValue(String value) {
        selectedSound = value;
    }

    void composeContacts(EditText target) {
        contactTarget = target;
        chooseContacts(target);
    }

    void composeContactsClosed() {
        contactTarget = null;
    }

    private void toggleMonitor(boolean enabled) {
        if (!enabled && !granted(Manifest.permission.READ_SMS)) {
            requestPermissions(new String[] {Manifest.permission.READ_SMS}, 5);
            return;
        }
        if (!enabled && needsSms() && !granted(Manifest.permission.RECEIVE_SMS)) {
            requestPermissions(new String[] {Manifest.permission.RECEIVE_SMS}, 1);
            return;
        }
        Store.log(this, "MONITOR_REQUEST enabled=" + !enabled);
        if (enabled) {
            Store.prefs(this).edit().putBoolean("monitor", false).commit();
            startService(new Intent(this, MacroService.class).setAction("STOP"));
        } else {
            Store.prefs(this)
                    .edit()
                    .putBoolean("monitor", true)
                    .putLong("monitorSince", System.currentTimeMillis())
                    .commit();
            try {
                startForegroundService(new Intent(this, MacroService.class));
            } catch (RuntimeException e) {
                Store.prefs(this).edit().putBoolean("monitor", false).commit();
                Store.log(this, "MONITOR_START_FAILED " + e.getMessage());
                toast(e.getMessage());
            }
        }
        render();
    }

    private void testRule(JSONObject r) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.test_macro_title))
                .setMessage(getString(R.string.test_macro_description))
                .setPositiveButton(
                        getString(R.string.start_test),
                        (d, w) -> {
                            try {
                                startForegroundService(
                                        new Intent(this, MacroService.class)
                                                .setAction("RUN")
                                                .putExtra("rule", r.toString())
                                                .putExtra(
                                                        "source",
                                                        getString(R.string.manual_test_source)));
                            } catch (RuntimeException e) {
                                toast(e.getMessage());
                            }
                        })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private EditText field(LinearLayout box, String label, String value, int type) {
        text(box, label, 14);
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setText(value);
        e.setTextColor(ui.ink);
        e.setTextSize(16);
        e.setHintTextColor(ui.muted);
        e.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        android.graphics.drawable.GradientDrawable bg = ui.shape(ui.container, 12);
        bg.setStroke(ui.dp(1), ui.outline);
        e.setBackground(bg);
        e.setMinHeight(ui.dp(56));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = ui.dp(12);
        box.addView(e, lp);
        return e;
    }

    private void chooseContacts(EditText target) {
        if (contactTarget != target) return;
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.read_contacts_title))
                    .setMessage(getString(R.string.read_contacts_description))
                    .setPositiveButton(
                            getString(R.string.continue_permission),
                            (d, w) ->
                                    requestPermissions(
                                            new String[] {Manifest.permission.READ_CONTACTS}, 3))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }
        toast(getString(R.string.loading_contacts));
        new Thread(
                        () -> {
                            LinkedHashMap<String, String> contacts = new LinkedHashMap<>();
                            LinkedHashMap<String, String> names = new LinkedHashMap<>();
                            try (Cursor cursor =
                                    getContentResolver()
                                            .query(
                                                    Phone.CONTENT_URI,
                                                    new String[] {Phone.DISPLAY_NAME, Phone.NUMBER},
                                                    null,
                                                    null,
                                                    Phone.DISPLAY_NAME + " ASC")) {
                                if (cursor != null)
                                    while (cursor.moveToNext()) {
                                        String raw = cursor.getString(1);
                                        if (raw == null) continue;
                                        String n = Store.normalize(raw);
                                        if (!n.isEmpty() && !contacts.containsKey(n)) {
                                            String displayName = cursor.getString(0);
                                            contacts.put(
                                                    n,
                                                    (displayName == null
                                                                    ? getString(
                                                                            R.string
                                                                                    .unnamed_contact)
                                                                    : displayName)
                                                            + " · "
                                                            + raw);
                                            if (displayName != null
                                                    && !displayName.trim().isEmpty())
                                                names.put(n, displayName);
                                        }
                                    }
                            } catch (RuntimeException e) {
                                runOnUiThread(
                                        () -> {
                                            if (!isFinishing() && !isDestroyed())
                                                toast(
                                                        getString(
                                                                R.string.contacts_read_failed,
                                                                e.getMessage()));
                                        });
                                return;
                            }
                            runOnUiThread(
                                    () -> {
                                        if (isFinishing()
                                                || isDestroyed()
                                                || contactTarget != target) return;
                                        if (contacts.isEmpty()) {
                                            toast(getString(R.string.contacts_empty));
                                            return;
                                        }
                                        showContacts(target, contacts, names);
                                    });
                        },
                        "contact-reader")
                .start();
    }

    private void showContacts(
            EditText target,
            LinkedHashMap<String, String> contacts,
            LinkedHashMap<String, String> names) {
        LinkedHashSet<String> selected = Store.parseNumbers(target.getText().toString());
        ArrayList<String> visible = new ArrayList<>();
        LinearLayout box = column();
        text(box, getString(R.string.contacts_selection_hint), 14);
        EditText search =
                field(box, getString(R.string.search_contacts), "", InputType.TYPE_CLASS_TEXT);
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        box.addView(
                list,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (320 * getResources().getDisplayMetrics().density)));
        Runnable refresh =
                () -> {
                    String query = search.getText().toString().toLowerCase(Locale.ROOT).trim();
                    visible.clear();
                    ArrayList<String> labels = new ArrayList<>();
                    for (Map.Entry<String, String> entry : contacts.entrySet()) {
                        if (entry.getValue().toLowerCase(Locale.ROOT).contains(query)
                                || entry.getKey().contains(query)) {
                            visible.add(entry.getKey());
                            labels.add(entry.getValue());
                        }
                    }
                    list.setAdapter(
                            new ArrayAdapter<>(
                                    this,
                                    android.R.layout.simple_list_item_multiple_choice,
                                    labels));
                    list.clearChoices();
                    for (int i = 0; i < visible.size(); i++)
                        list.setItemChecked(i, selected.contains(visible.get(i)));
                };
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String n = visible.get(position);
                    if (list.isItemChecked(position)) selected.add(n);
                    else selected.remove(n);
                });
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        refresh.run();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        refresh.run();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_contacts_title))
                .setView(box)
                .setPositiveButton(
                        getString(R.string.use_selected_numbers),
                        (d, w) -> {
                            if (contactTarget != target) return;
                            target.setText(String.join("\n", selected));
                        })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void launch(Intent intent, int code) {
        try {
            startActivityForResult(intent, code);
        } catch (RuntimeException e) {
            toast(getString(R.string.page_unavailable, e.getMessage()));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if ((request == 30 || request == 31) && data.getData() != null) {
            backupFile(request, data.getData());
            return;
        }
        Uri uri =
                request == 10
                        ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                        : data.getData();
        if ((request == 10 || request == 11) && uri != null) {
            if (request == 11)
                try {
                    getContentResolver()
                            .takePersistableUriPermission(
                                    uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException e) {
                    toast(getString(R.string.sound_permission_failed));
                    return;
                }
            selectedSound = uri.toString();
            render();
        }
    }

    private void backupFile(int request, Uri uri) {
        String snapshot = Store.rules(this).toString();
        toast(
                request == 30
                        ? getString(R.string.exporting_macros)
                        : getString(R.string.loading_backup));
        new Thread(
                        () -> {
                            try {
                                if (request == 30) {
                                    byte[] bytes =
                                            MacroBackup.exportRules(
                                                            new JSONArray(snapshot),
                                                            getString(
                                                                    R.string.default_alert_message))
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8);
                                    if (bytes.length > MacroBackup.MAX_BYTES)
                                        throw new java.io.IOException(
                                                getString(R.string.backup_size_exceeded));
                                    try (java.io.OutputStream out =
                                            getContentResolver().openOutputStream(uri, "wt")) {
                                        if (out == null)
                                            throw new java.io.IOException(
                                                    getString(R.string.backup_output_unavailable));
                                        out.write(bytes);
                                        out.flush();
                                    }
                                    runOnUiThread(
                                            () -> {
                                                if (!isFinishing() && !isDestroyed())
                                                    toast(getString(R.string.backup_exported));
                                            });
                                } else {
                                    java.io.ByteArrayOutputStream bytes =
                                            new java.io.ByteArrayOutputStream();
                                    try (java.io.InputStream in =
                                            getContentResolver().openInputStream(uri)) {
                                        if (in == null)
                                            throw new java.io.IOException(
                                                    getString(R.string.backup_input_unavailable));
                                        byte[] buffer = new byte[8192];
                                        int count;
                                        while ((count = in.read(buffer)) != -1) {
                                            if (bytes.size() + count > MacroBackup.MAX_BYTES)
                                                throw new java.io.IOException(
                                                        getString(R.string.backup_size_exceeded));
                                            bytes.write(buffer, 0, count);
                                        }
                                    }
                                    JSONArray imported =
                                            MacroBackup.importRules(
                                                    new String(
                                                            bytes.toByteArray(),
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8),
                                                    getString(R.string.default_alert_message));
                                    runOnUiThread(
                                            () -> {
                                                if (!isFinishing() && !isDestroyed())
                                                    confirmImport(imported);
                                            });
                                }
                            } catch (Exception e) {
                                runOnUiThread(
                                        () -> {
                                            if (!isFinishing() && !isDestroyed())
                                                toast(
                                                        getString(
                                                                R.string.backup_failed,
                                                                BackupMessages.describe(this, e)));
                                        });
                            }
                        },
                        "macro-backup")
                .start();
    }

    private void confirmImport(JSONArray imported) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.restore_title, imported.length()))
                .setMessage(getString(R.string.restore_description))
                .setPositiveButton(
                        getString(R.string.append), (d, w) -> applyImport(imported, false))
                .setNeutralButton(
                        getString(R.string.replace_all),
                        (d, w) ->
                                new AlertDialog.Builder(this)
                                        .setTitle(getString(R.string.replace_all_title))
                                        .setMessage(
                                                getString(
                                                        R.string.replace_all_description,
                                                        imported.length()))
                                        .setPositiveButton(
                                                getString(R.string.confirm_replace),
                                                (dialog, which) -> applyImport(imported, true))
                                        .setNegativeButton(getString(R.string.cancel), null)
                                        .show())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void applyImport(JSONArray imported, boolean replace) {
        JSONArray combined = replace ? new JSONArray() : Store.rules(this);
        if (combined.length() + imported.length() > 500) {
            toast(getString(R.string.restore_count_exceeded));
            return;
        }
        for (int i = 0; i < imported.length(); i++) combined.put(imported.optJSONObject(i));
        boolean saved =
                Store.prefs(this)
                        .edit()
                        .putString("rules", combined.toString())
                        .putBoolean("monitor", false)
                        .commit();
        if (!saved) {
            toast(getString(R.string.save_failed));
            return;
        }
        try {
            startService(new Intent(this, MacroService.class).setAction("STOP"));
        } catch (RuntimeException e) {
            Store.log(this, getString(R.string.log_restore_stop_failed) + e.getMessage());
        }
        render();
        toast(getString(R.string.restore_complete, imported.length()));
    }

    private void recentSmsDiagnostics() {
        if (readingRecentSms) {
            toast(getString(R.string.reading_please_wait));
            return;
        }
        if (!granted(Manifest.permission.READ_SMS)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.recent_messages_title))
                    .setMessage(getString(R.string.recent_messages_permission_description))
                    .setPositiveButton(
                            getString(R.string.request_permission),
                            (d, w) ->
                                    requestPermissions(
                                            new String[] {Manifest.permission.READ_SMS}, 4))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }
        readingRecentSms = true;
        toast(getString(R.string.loading_inbox));
        Context app = getApplicationContext();
        new Thread(
                        () -> {
                            try {
                                RecentSmsDiagnostics.Result result = RecentSmsDiagnostics.read(app);
                                Store.log(
                                        app,
                                        "RECENT_SMS_DIAGNOSTIC success rows="
                                                + result.count
                                                + getString(R.string.log_recent_messages_privacy));
                                runOnUiThread(
                                        () -> {
                                            readingRecentSms = false;
                                            if (isFinishing() || isDestroyed()) return;
                                            LinearLayout box = column();
                                            TextView report = text(box, result.text, 15);
                                            report.setTextIsSelectable(true);
                                            ScrollView scroll = new ScrollView(this);
                                            scroll.addView(box);
                                            new AlertDialog.Builder(this)
                                                    .setTitle(
                                                            getString(
                                                                    R.string.recent_messages_count,
                                                                    result.count))
                                                    .setView(scroll)
                                                    .setPositiveButton(
                                                            getString(R.string.close), null)
                                                    .setNeutralButton(
                                                            getString(R.string.reload),
                                                            (d, w) -> recentSmsDiagnostics())
                                                    .show();
                                        });
                            } catch (RuntimeException e) {
                                Store.log(
                                        app,
                                        "RECENT_SMS_DIAGNOSTIC failed="
                                                + e.getClass().getSimpleName());
                                runOnUiThread(
                                        () -> {
                                            readingRecentSms = false;
                                            if (isFinishing() || isDestroyed()) return;
                                            new AlertDialog.Builder(this)
                                                    .setTitle(
                                                            getString(
                                                                    R.string
                                                                            .recent_messages_failed))
                                                    .setMessage(
                                                            getString(
                                                                    R.string.recent_messages_error,
                                                                    e.getClass().getSimpleName(),
                                                                    e.getMessage()))
                                                    .setPositiveButton(
                                                            getString(R.string.app_permissions),
                                                            (d, w) ->
                                                                    launch(
                                                                            new Intent(
                                                                                    Settings
                                                                                            .ACTION_APPLICATION_DETAILS_SETTINGS,
                                                                                    Uri.parse(
                                                                                            "package:"
                                                                                                    + getPackageName())),
                                                                            20))
                                                    .setNegativeButton(
                                                            getString(R.string.close), null)
                                                    .show();
                                        });
                            }
                        },
                        "recent-sms-diagnostic")
                .start();
    }

    private String permissionLabel(boolean granted, int label) {
        return getString(
                R.string.permission_label,
                getString(
                        granted
                                ? R.string.permission_status_granted
                                : R.string.permission_status_missing),
                getString(label));
    }

    void composeThemeChanged() {
        setTheme(
                "play".equals(Store.prefs(this).getString("colorStyle", "x"))
                        ? R.style.AppTheme_Play
                        : R.style.AppTheme);
        ui = new DialogUi(this);
    }

    private void permissions() {
        LinearLayout box = column();
        text(box, getString(R.string.permission_privacy_description), 16);
        button(
                box,
                permissionLabel(
                        granted(Manifest.permission.RECEIVE_SMS), R.string.permission_receive_sms),
                () -> requestPermissions(new String[] {Manifest.permission.RECEIVE_SMS}, 1));
        button(
                box,
                permissionLabel(
                        granted(Manifest.permission.READ_SMS), R.string.permission_read_sms),
                this::recentSmsDiagnostics);
        button(
                box,
                permissionLabel(
                        granted(Manifest.permission.READ_CONTACTS), R.string.permission_contacts),
                () -> requestPermissions(new String[] {Manifest.permission.READ_CONTACTS}, 3));
        if (Build.VERSION.SDK_INT >= 33)
            button(
                    box,
                    permissionLabel(
                            granted(Manifest.permission.POST_NOTIFICATIONS),
                            R.string.permission_notifications),
                    () ->
                            requestPermissions(
                                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, 2));
        button(
                box,
                permissionLabel(Settings.canDrawOverlays(this), R.string.permission_overlay),
                () ->
                        launch(
                                new Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName())),
                                20));
        button(
                box,
                getString(R.string.alert_notification_settings),
                () -> {
                    MacroService.ensureAlertChannel(this);
                    launch(
                            new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, "alerts"),
                            20);
                });
        NotificationManager nm = getSystemService(NotificationManager.class);
        button(
                box,
                permissionLabel(nm.isNotificationPolicyAccessGranted(), R.string.permission_dnd),
                () -> launch(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), 20));
        boolean exempt =
                getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName());
        button(
                box,
                permissionLabel(exempt, R.string.battery_settings),
                () -> launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), 20));
        button(
                box,
                getString(R.string.app_settings),
                () ->
                        launch(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName())),
                                20));
        text(box, getString(R.string.background_limitations), 14);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.permissions_dialog_title))
                .setView(scroll)
                .setPositiveButton(getString(R.string.close), null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        toast(
                g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED
                        ? getString(R.string.permission_granted)
                        : getString(R.string.permission_denied));
        render();
        if (r == 3
                && g.length > 0
                && g[0] == PackageManager.PERMISSION_GRANTED
                && contactTarget != null) chooseContacts(contactTarget);
        if (r == 4 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED)
            recentSmsDiagnostics();
        if ((r == 4 || r == 5)
                && g.length > 0
                && g[0] == PackageManager.PERMISSION_GRANTED
                && Store.prefs(this).getBoolean("monitor", false)) {
            try {
                startForegroundService(
                        new Intent(this, MacroService.class).setAction("SCAN_INBOX"));
            } catch (RuntimeException e) {
                toast(e.getMessage());
            }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
