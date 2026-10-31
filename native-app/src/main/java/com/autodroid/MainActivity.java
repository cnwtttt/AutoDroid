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

public class MainActivity extends Activity {
    private LinearLayout root;
    private MaterialUi ui;
    private int selectedPage;
    private int renderedPage;
    private ScrollView activeScroll;
    private final int[] scrollPositions = new int[3];
    private boolean readingRecentSms;
    private EditText contactTarget;
    private String selectedSound = "";
    private TextView soundLabel;

    @Override
    public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme_Play);
        super.onCreate(state);
        ui = new MaterialUi(this);
        if (state != null) selectedPage = state.getInt("page", 0);
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

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", selectedPage);
        super.onSaveInstanceState(state);
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
        if (activeScroll != null) scrollPositions[renderedPage] = activeScroll.getScrollY();
        renderedPage = selectedPage;
        LinearLayout shell = ui.column(0);
        shell.setBackgroundColor(ui.background);
        setContentView(shell);
        shell.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    } else
                        view.setPadding(
                                insets.getSystemWindowInsetLeft(),
                                insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(),
                                insets.getSystemWindowInsetBottom());
                    return insets;
                });
        shell.requestApplyInsets();
        getWindow().setStatusBarColor(ui.background);
        getWindow().setNavigationBarColor(ui.container);
        if (Build.VERSION.SDK_INT >= 30)
            getWindow()
                    .getInsetsController()
                    .setSystemBarsAppearance(
                            ui.background == 0xFFF8F9FF
                                    ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                            | WindowInsetsController
                                                    .APPEARANCE_LIGHT_NAVIGATION_BARS
                                    : 0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout bar = ui.row();
        bar.setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(12));
        TextView brand = ui.label(getString(R.string.app_name), 22, ui.ink, true);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = ui.label(getString(R.string.local_only_badge), 12, ui.onAccent, false);
        local.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        local.setBackground(ui.shape(ui.accent, 16));
        bar.addView(local);
        shell.addView(bar);
        FrameLayout body = new FrameLayout(this);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        activeScroll = scroll;
        final int restoreY = scrollPositions[selectedPage];
        scroll.post(() -> scroll.scrollTo(0, restoreY));
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout wrapper = ui.column(0);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(wrapper);
        root = column();
        root.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), ui.dp(selectedPage == 0 ? 104 : 24));
        int width = getResources().getConfiguration().screenWidthDp;
        wrapper.addView(root, new LinearLayout.LayoutParams(width > 640 ? ui.dp(640) : -1, -2));
        if (selectedPage == 0) renderMacros();
        else if (selectedPage == 1) renderLogs();
        else renderSettings();
        if (selectedPage == 0) {
            Button fab = ui.button(getString(R.string.create_macro), true, () -> edit(-1));
            fab.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            android.graphics.drawable.Drawable plus = ui.icon("add", ui.onPrimary).getDrawable();
            plus.setBounds(0, 0, ui.dp(24), ui.dp(24));
            fab.setCompoundDrawables(plus, null, null, null);
            fab.setCompoundDrawablePadding(ui.dp(8));
            fab.setElevation(ui.dp(4));
            fab.setMinHeight(ui.dp(56));
            fab.setBackground(ui.ripple(ui.primary, 16));
            FrameLayout.LayoutParams fp =
                    new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            fp.setMargins(ui.dp(24), 0, ui.dp(24), ui.dp(20));
            body.addView(fab, fp);
        }
        LinearLayout nav = ui.row();
        nav.setBaselineAligned(false);
        nav.setBackgroundColor(ui.container);
        nav.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        String[]
                labels =
                        {
                            getString(R.string.nav_automation),
                            getString(R.string.nav_logs),
                            getString(R.string.nav_settings)
                        },
                icons = {"macros", "logs", "settings"};
        for (int i = 0; i < 3; i++) {
            final int page = i;
            boolean active = i == selectedPage;
            LinearLayout item = ui.column(4);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(ui.dp(64));
            FrameLayout pill = new FrameLayout(this);
            pill.setBackground(ui.shape(active ? ui.accent : ui.container, 20));
            FrameLayout.LayoutParams ip =
                    new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER);
            pill.addView(ui.icon(icons[i], active ? ui.onAccent : ui.muted), ip);
            LinearLayout.LayoutParams pillParams =
                    new LinearLayout.LayoutParams(ui.dp(64), ui.dp(32));
            pillParams.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(pill, pillParams);
            TextView label = ui.label(labels[i], 12, active ? ui.ink : ui.muted, active);
            label.setGravity(Gravity.CENTER);
            label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            label.setSingleLine(true);
            label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = ui.dp(4);
            item.addView(label, labelParams);
            item.setBackground(ui.ripple(ui.container, 20));
            item.setSelected(active);
            item.setContentDescription(
                    labels[i] + (active ? getString(R.string.native_selected_suffix) : ""));
            item.setOnClickListener(
                    v -> {
                        selectedPage = page;
                        render();
                    });
            nav.addView(
                    item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        shell.addView(
                nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(80)));
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

    private void renderMacros() {
        text(root, getString(R.string.home_title), 28);
        text(root, getString(R.string.home_subtitle), 14);
        ui.space(root, 16);
        boolean enabled = Store.prefs(this).getBoolean("monitor", false);
        LinearLayout status = ui.card(root, ui.accent);
        LinearLayout row = ui.row();
        row.addView(
                ui.icon("message", ui.onAccent),
                new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        TextView title =
                ui.label(
                        enabled
                                ? getString(R.string.monitor_enabled)
                                : getString(R.string.monitor_paused),
                        20,
                        ui.onAccent,
                        true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = ui.dp(12);
        row.addView(title, tp);
        status.addView(row);
        text(
                status,
                enabled
                        ? getString(R.string.native_monitor_enabled_description)
                        : getString(R.string.native_monitor_paused_description),
                14);
        Button control =
                ui.button(
                        enabled
                                ? getString(R.string.pause_monitor)
                                : getString(R.string.start_monitor),
                        true,
                        () -> toggleMonitor(enabled));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = ui.dp(12);
        status.addView(control, cp);
        if (!granted(Manifest.permission.READ_SMS)
                || !Settings.canDrawOverlays(this)
                || (Build.VERSION.SDK_INT >= 33
                        && !granted(Manifest.permission.POST_NOTIFICATIONS))) {
            LinearLayout notice = ui.card(root, ui.container);
            text(notice, getString(R.string.complete_permissions), 18);
            text(notice, getString(R.string.native_permission_hint), 14);
            button(notice, getString(R.string.check_permissions), this::permissions);
        }
        JSONArray rules = Store.rules(this);
        int enabledCount = 0;
        for (int i = 0; i < rules.length(); i++)
            if (rules.optJSONObject(i) != null
                    && rules.optJSONObject(i).optBoolean("enabled", true)) enabledCount++;
        text(root, getString(R.string.my_macros), 22);
        text(root, getString(R.string.native_macro_count, rules.length(), enabledCount), 13);
        ui.space(root, 12);
        if (rules.length() == 0) {
            LinearLayout empty = ui.card(root, ui.surface);
            empty.setGravity(Gravity.CENTER);
            empty.addView(
                    ui.icon("macros", ui.primary),
                    new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            text(empty, getString(R.string.empty_macros_title), 20);
            text(empty, getString(R.string.native_empty_macros_description), 14);
            button(empty, getString(R.string.create_sms_macro), () -> edit(-1));
        }
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r == null) continue;
            final int index = i;
            LinearLayout card = ui.card(root, ui.surface);
            LinearLayout header = ui.row();
            TextView name = ui.label(r.optString("name"), 20, ui.ink, true);
            header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Switch toggle = new Switch(this);
            toggle.setChecked(r.optBoolean("enabled", true));
            toggle.setContentDescription(
                    r.optString("name") + getString(R.string.native_enable_macro_suffix));
            toggle.setMinimumWidth(ui.dp(56));
            toggle.setMinimumHeight(ui.dp(48));
            toggle.setThumbTintList(
                    new android.content.res.ColorStateList(
                            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                            new int[] {ui.primary, ui.muted}));
            header.addView(toggle);
            card.addView(header);
            toggle.setOnCheckedChangeListener(
                    (v, checked) -> {
                        try {
                            r.put("enabled", checked);
                            Store.save(this, rules);
                            render();
                        } catch (JSONException e) {
                            toast(e.getMessage());
                        }
                    });
            String numbers = String.join("、", Store.numbers(r));
            TextView sender = text(card, getString(R.string.native_sender_prefix) + numbers, 14);
            sender.setMaxLines(2);
            sender.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text(
                    card,
                    r.optString("keyword").isEmpty()
                            ? getString(R.string.match_any_content)
                            : getString(R.string.native_keyword_prefix)
                                    + r.optString("keyword")
                                    + "”",
                    14);
            ui.space(card, 8);
            TextView chip =
                    ui.label(
                            getResources()
                                            .getStringArray(R.array.audio_channels)[
                                            Math.max(0, Math.min(3, r.optInt("channel")))]
                                    + "  ·  "
                                    + r.optInt("volume")
                                    + "%  ·  "
                                    + r.optInt("duration")
                                    + getString(R.string.native_duration_suffix),
                            12,
                            ui.onAccent,
                            false);
            chip.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
            chip.setBackground(ui.shape(ui.accent, 12));
            card.addView(chip);
            ui.space(card, 16);
            LinearLayout actions = ui.row();
            Button test = ui.button(getString(R.string.test), false, () -> testRule(r));
            actions.addView(test, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = ui.button(getString(R.string.edit), false, () -> edit(index));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, -2, 1);
            ep.leftMargin = ui.dp(8);
            actions.addView(edit, ep);
            Button more =
                    ui.button(
                            "⋮",
                            false,
                            () ->
                                    new AlertDialog.Builder(this)
                                            .setTitle(r.optString("name"))
                                            .setItems(
                                                    new String[] {
                                                        getString(R.string.native_delete_action)
                                                    },
                                                    (d, w) ->
                                                            new AlertDialog.Builder(this)
                                                                    .setTitle(
                                                                            getString(
                                                                                    R.string
                                                                                            .native_delete_title))
                                                                    .setMessage(
                                                                            getString(
                                                                                    R.string
                                                                                            .delete_macro_description))
                                                                    .setPositiveButton(
                                                                            getString(
                                                                                    R.string
                                                                                            .delete),
                                                                            (a, b) -> {
                                                                                rules.remove(index);
                                                                                Store.save(
                                                                                        this,
                                                                                        rules);
                                                                                render();
                                                                            })
                                                                    .setNegativeButton(
                                                                            getString(
                                                                                    R.string
                                                                                            .cancel),
                                                                            null)
                                                                    .show())
                                            .show());
            more.setContentDescription(
                    getString(R.string.native_more_actions_prefix) + r.optString("name"));
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ui.dp(48), -2);
            mp.leftMargin = ui.dp(8);
            more.setPadding(0, 0, 0, 0);
            actions.addView(more, mp);
            card.addView(actions);
        }
        button(
                root,
                getString(R.string.stop_current_alert),
                () -> startService(new Intent(this, MacroService.class).setAction("SILENCE")));
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

    private void renderLogs() {
        text(root, getString(R.string.execution_logs), 28);
        text(root, getString(R.string.native_logs_description), 14);
        ui.space(root, 16);
        LinearLayout actions = ui.row();
        actions.addView(
                ui.button(getString(R.string.refresh), false, this::render),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button clear =
                ui.button(
                        getString(R.string.clear_logs),
                        false,
                        () ->
                                new AlertDialog.Builder(this)
                                        .setTitle(getString(R.string.clear_logs_title))
                                        .setPositiveButton(
                                                getString(R.string.clear),
                                                (d, w) -> {
                                                    Store.prefs(this).edit().remove("logs").apply();
                                                    render();
                                                })
                                        .setNegativeButton(getString(R.string.cancel), null)
                                        .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = ui.dp(8);
        actions.addView(clear, lp);
        root.addView(actions);
        ui.space(root, 16);
        LinearLayout card = ui.card(root, ui.surface);
        TextView logs =
                text(card, Store.prefs(this).getString("logs", getString(R.string.logs_empty)), 13);
        logs.setTextIsSelectable(true);
    }

    private void renderSettings() {
        text(root, getString(R.string.nav_settings), 28);
        text(root, getString(R.string.settings_description), 14);
        ui.space(root, 16);
        LinearLayout permissions = ui.card(root, ui.surface);
        text(permissions, getString(R.string.permissions_title), 20);
        text(permissions, getString(R.string.permissions_description), 14);
        button(permissions, getString(R.string.manage_permissions), this::permissions);
        LinearLayout backup = ui.card(root, ui.surface);
        text(backup, getString(R.string.backup_title), 20);
        text(backup, getString(R.string.native_backup_description), 14);
        button(
                backup,
                getString(R.string.export_macros),
                () ->
                        launch(
                                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("application/json")
                                        .putExtra(
                                                Intent.EXTRA_TITLE,
                                                "AutoDroid-macros-"
                                                        + new java.text.SimpleDateFormat(
                                                                        "yyyyMMdd-HHmmss",
                                                                        Locale.ROOT)
                                                                .format(new Date())
                                                        + ".json"),
                                30));
        button(
                backup,
                getString(R.string.restore_macros),
                () ->
                        launch(
                                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("*/*"),
                                31));
        LinearLayout diagnose = ui.card(root, ui.surface);
        text(diagnose, getString(R.string.diagnostics_title), 20);
        text(diagnose, getString(R.string.native_diagnostics_description), 14);
        button(diagnose, getString(R.string.read_recent_messages), this::recentSmsDiagnostics);
        LinearLayout about = ui.card(root, ui.container);
        text(about, getString(R.string.privacy_title), 18);
        text(about, getString(R.string.privacy_description), 14);
        text(about, getString(R.string.native_about_version, BuildConfig.VERSION_NAME), 12);
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

    private void edit(int index) {
        JSONArray rules = Store.rules(this);
        JSONObject old = index < 0 ? new JSONObject() : rules.optJSONObject(index);
        LinearLayout form = column();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        text(form, getString(R.string.native_general_section), 22);
        EditText name =
                field(
                        form,
                        getString(R.string.macro_name),
                        old.optString("name", getString(R.string.default_macro_name)),
                        InputType.TYPE_CLASS_TEXT);
        text(form, getString(R.string.trigger_conditions), 22);
        text(form, getString(R.string.native_trigger_hint), 14);
        EditText number =
                field(
                        form,
                        getString(R.string.native_numbers_label),
                        Store.numberText(old),
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        number.setMinLines(2);
        contactTarget = number;
        button(form, getString(R.string.native_choose_contacts), () -> chooseContacts(number));
        EditText keyword =
                field(
                        form,
                        getString(R.string.native_keyword_label),
                        old.optString("keyword"),
                        InputType.TYPE_CLASS_TEXT);
        text(form, getString(R.string.sound_section), 22);
        text(form, getString(R.string.native_audio_channel_label), 15);
        Spinner channel = new Spinner(this);
        channel.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        getResources().getStringArray(R.array.audio_channels)));
        channel.setMinimumHeight(ui.dp(48));
        channel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.primary));
        channel.setSelection(Math.max(0, Math.min(3, old.optInt("channel"))));
        form.addView(channel);
        text(form, getString(R.string.native_volume_label), 15);
        SeekBar volume = new SeekBar(this);
        volume.setMinimumHeight(ui.dp(48));
        volume.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.primary));
        volume.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.primary));
        volume.setMax(100);
        volume.setProgress(old.optInt("volume", 80));
        form.addView(volume);
        TextView value = text(form, volume.getProgress() + "%", 15);
        volume.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int p, boolean user) {
                        value.setText(p + "%");
                    }

                    public void onStartTrackingTouch(SeekBar s) {}

                    public void onStopTrackingTouch(SeekBar s) {}
                });
        EditText duration =
                field(
                        form,
                        getString(R.string.native_duration_label),
                        String.valueOf(old.optInt("duration", 60)),
                        InputType.TYPE_CLASS_NUMBER);
        text(form, getString(R.string.alert_content), 22);
        EditText message =
                field(
                        form,
                        getString(R.string.native_message_label),
                        old.optString("message", getString(R.string.default_alert_message)),
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        selectedSound = old.optString("sound", "");
        soundLabel =
                text(
                        form,
                        selectedSound.isEmpty()
                                ? getString(R.string.default_sound_label)
                                : getString(R.string.custom_sound_label),
                        13);
        button(
                form,
                getString(R.string.native_choose_ringtone),
                () -> {
                    Intent picker =
                            new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(
                                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                                            RingtoneManager.TYPE_ALL)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    launch(picker, 10);
                });
        button(
                form,
                getString(R.string.native_choose_audio_file),
                () ->
                        launch(
                                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .setType("audio/*")
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        | Intent
                                                                .FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                                11));
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                index < 0
                                        ? getString(R.string.new_sms_macro)
                                        : getString(R.string.edit_sms_macro))
                        .setView(scroll)
                        .setPositiveButton(getString(R.string.save), null)
                        .setNegativeButton(getString(R.string.cancel), null)
                        .create();
        dialog.setOnDismissListener(
                d -> {
                    if (contactTarget == number) {
                        contactTarget = null;
                    }
                });
        dialog.setOnShowListener(
                v ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        b -> {
                                            LinkedHashSet<String> numbers =
                                                    Store.parseNumbers(number.getText().toString());
                                            if (name.getText().toString().trim().isEmpty()
                                                    || numbers.isEmpty()) {
                                                toast(getString(R.string.native_required_fields));
                                                return;
                                            }
                                            int seconds;
                                            try {
                                                seconds =
                                                        Integer.parseInt(
                                                                duration.getText().toString());
                                            } catch (NumberFormatException e) {
                                                seconds = 0;
                                            }
                                            if (seconds < 5 || seconds > 600) {
                                                toast(getString(R.string.error_duration_range));
                                                return;
                                            }
                                            try {
                                                JSONObject r =
                                                        new JSONObject()
                                                                .put("source", "sms")
                                                                .put(
                                                                        "name",
                                                                        name.getText()
                                                                                .toString()
                                                                                .trim())
                                                                .put(
                                                                        "numbers",
                                                                        new JSONArray(numbers))
                                                                .put(
                                                                        "keyword",
                                                                        keyword.getText()
                                                                                .toString())
                                                                .put(
                                                                        "channel",
                                                                        channel
                                                                                .getSelectedItemPosition())
                                                                .put("volume", volume.getProgress())
                                                                .put("duration", seconds)
                                                                .put(
                                                                        "message",
                                                                        message.getText()
                                                                                .toString())
                                                                .put("sound", selectedSound)
                                                                .put(
                                                                        "enabled",
                                                                        old.optBoolean(
                                                                                "enabled", true));
                                                if (index < 0) rules.put(r);
                                                else rules.put(index, r);
                                                Store.save(this, rules);
                                                dialog.dismiss();
                                                render();
                                            } catch (JSONException e) {
                                                toast(e.getMessage());
                                            }
                                        }));
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow()
                    .setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (int) (getResources().getDisplayMetrics().heightPixels * 0.9f));
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
            if (soundLabel != null) soundLabel.setText(getString(R.string.custom_sound_label));
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
                                                            e.getClass().getSimpleName()
                                                                    + "："
                                                                    + e.getMessage()
                                                                    + getString(
                                                                            R.string
                                                                                    .native_read_sms_error_hint))
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

    private void permissions() {
        LinearLayout box = column();
        text(box, getString(R.string.permission_privacy_description), 16);
        button(
                box,
                (granted(Manifest.permission.RECEIVE_SMS) ? "✓ " : "○ ")
                        + getString(R.string.permission_receive_sms),
                () -> requestPermissions(new String[] {Manifest.permission.RECEIVE_SMS}, 1));
        button(
                box,
                (granted(Manifest.permission.READ_SMS) ? "✓ " : "○ ")
                        + getString(R.string.permission_read_sms),
                this::recentSmsDiagnostics);
        button(
                box,
                (granted(Manifest.permission.READ_CONTACTS) ? "✓ " : "○ ")
                        + getString(R.string.permission_contacts),
                () -> requestPermissions(new String[] {Manifest.permission.READ_CONTACTS}, 3));
        if (Build.VERSION.SDK_INT >= 33)
            button(
                    box,
                    (granted(Manifest.permission.POST_NOTIFICATIONS) ? "✓ " : "○ ")
                            + getString(R.string.permission_notifications),
                    () ->
                            requestPermissions(
                                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, 2));
        button(
                box,
                (Settings.canDrawOverlays(this) ? "✓ " : "○ ")
                        + getString(R.string.permission_overlay),
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
                (nm.isNotificationPolicyAccessGranted() ? "✓ " : "○ ")
                        + getString(R.string.permission_dnd),
                () -> launch(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), 20));
        boolean exempt =
                getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName());
        button(
                box,
                (exempt ? "✓ " : "○ ") + getString(R.string.battery_settings),
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
