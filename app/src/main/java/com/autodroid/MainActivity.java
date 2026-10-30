package com.autodroid;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.database.Cursor;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private EditText contactTarget;
    private EditText contactNameTarget;
    private String selectedSound = "";
    private TextView soundLabel;
    private static final String[] CHANNELS = {"闹钟", "媒体", "铃声", "通知"};
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override public void onResume() {
        super.onResume(); render();
        Store.log(this, "APP_RESUME version=0.3.0 monitor=" + Store.prefs(this).getBoolean("monitor", false)
            + " receiveSmsPermission=" + granted(Manifest.permission.RECEIVE_SMS)
            + " overlayPermission=" + Settings.canDrawOverlays(this)
            + " notificationAccess=" + notificationAccess());
        for (ActivityManager.AppTask task : getSystemService(ActivityManager.class).getAppTasks()) task.setExcludeFromRecents(true);
        if (Store.prefs(this).getBoolean("monitor",false) && !MacroService.isRunning) {
            try { Store.log(this,"MONITOR_RESTORE 恢复前台服务"); startForegroundService(new Intent(this,MacroService.class)); }
            catch (RuntimeException e) { Store.log(this,"MONITOR_RESTORE_FAILED " + e.getMessage()); }
        }
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); l.setPadding(24,16,24,16); return l; }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xFF18263A); t.setPadding(0,10,0,10); parent.addView(t); return t;
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); parent.addView(b); b.setOnClickListener(v -> action.run());
    }
    private boolean notificationAccess() {
        ComponentName component = new ComponentName(this,MessageNotificationService.class);
        if (Build.VERSION.SDK_INT >= 27) return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(component);
        String enabled = Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if (enabled != null) for (String item : enabled.split(":")) if (component.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }
    private boolean needsSms() {
        JSONArray rules = Store.rules(this);
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r != null && r.optBoolean("enabled",true) && !"notification".equals(r.optString("source"))) return true;
        }
        return false;
    }
    private boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
    private void render() {
        ScrollView scroll = new ScrollView(this); root = column(); root.setBackgroundColor(0xFFF2F5FA); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        text(root, "AutoDroid", 32); text(root, "短信到达 → 调节音量 → 播放音源 → 弹出警告", 15);
        boolean enabled = Store.prefs(this).getBoolean("monitor", false);
        text(root, enabled ? "● 已请求开启监听" : "○ 监听已关闭", 20);
        text(root,"短信应用通知访问：" + (notificationAccess() ? (MessageNotificationService.connected ? "已连接" : "已授权，等待系统连接") : "未授权（RCS/网络消息需要）"),14);
        button(root, enabled ? "关闭监听并停止声音" : "开启后台短信监听", () -> {
            if (!enabled && needsSms() && !granted(Manifest.permission.RECEIVE_SMS)) { requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 1); return; }
            Store.log(this, "MONITOR_REQUEST enabled=" + !enabled);
            if (enabled) startService(new Intent(this, MacroService.class).setAction("STOP"));
            else {
                Store.prefs(this).edit().putBoolean("monitor", true).putLong("monitorSince",System.currentTimeMillis()).commit();
                try { startForegroundService(new Intent(this, MacroService.class)); }
                catch (RuntimeException e) { Store.prefs(this).edit().putBoolean("monitor", false).commit(); Store.log(this,"MONITOR_START_FAILED " + e.getMessage()); toast(e.getMessage()); }
            }
            render();
        });
        button(root, "权限与后台设置", this::permissions);
        button(root, "＋ 新建短信宏", () -> edit(-1));
        JSONArray rules = Store.rules(this);
        if (rules.length() == 0) text(root, "还没有宏。先新建一条，再点手动测试。", 17);
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i); if (r == null) continue;
            final int index = i;
            LinearLayout card = column(); card.setBackgroundColor(0xFFFFFFFF); root.addView(card);
            Switch toggle = new Switch(this); toggle.setText(r.optString("name")); toggle.setChecked(r.optBoolean("enabled", true)); card.addView(toggle);
            toggle.setOnCheckedChangeListener((v, checked) -> { try { r.put("enabled", checked); Store.save(this, rules); } catch (JSONException e) { toast(e.getMessage()); } });
            text(card, "来源：" + ("notification".equals(r.optString("source")) ? "小米短信应用通知（含 RCS）" : "普通 SMS 广播") + "\n通知姓名：" + r.optString("senderNames","未配置") + "\n号码：" + String.join("、", Store.numbers(r)) + "\n关键词：" + (r.optString("keyword").isEmpty() ? "不限" : r.optString("keyword"))
                + "\n" + CHANNELS[Math.max(0,Math.min(3,r.optInt("channel")))] + "音量 " + r.optInt("volume") + "% · " + r.optInt("duration") + " 秒", 15);
            button(card, "手动测试（跳过短信条件，直接执行）", () -> {
                new AlertDialog.Builder(this).setTitle("执行真实动作")
                    .setMessage("将立即改变系统音量、播放声音并弹窗，确认后开始。禁用的宏也可以测试。")
                    .setPositiveButton("开始", (d,w) -> {
                        try { startForegroundService(new Intent(this, MacroService.class).setAction("RUN").putExtra("rule", r.toString()).putExtra("source", "手动测试")); }
                        catch (RuntimeException e) { toast(e.getMessage()); }
                    }).setNegativeButton("取消", null).show();
            });
            button(card, "编辑", () -> edit(index));
            button(card, "删除", () -> new AlertDialog.Builder(this).setMessage("删除这条宏？").setPositiveButton("删除", (d,w) -> { rules.remove(index); Store.save(this, rules); render(); }).setNegativeButton("取消",null).show());
        }
        button(root, "停止当前警告", () -> startService(new Intent(this, MacroService.class).setAction("SILENCE")));
        button(root, "查看执行日志", () -> new AlertDialog.Builder(this).setTitle("执行日志（不记录短信正文）").setMessage(Store.prefs(this).getString("logs", "暂无日志"))
            .setPositiveButton("关闭",null).setNeutralButton("清空", (d,w) -> Store.prefs(this).edit().remove("logs").apply()).show());
    }
    private EditText field(LinearLayout box, String label, String value, int type) {
        text(box,label,15); EditText e = new EditText(this); e.setInputType(type); e.setText(value); box.addView(e); return e;
    }
    private void edit(int index) {
        JSONArray rules = Store.rules(this); JSONObject old = index < 0 ? new JSONObject() : rules.optJSONObject(index);
        LinearLayout form = column(); ScrollView scroll = new ScrollView(this); scroll.addView(form);
        EditText name = field(form,"宏名称",old.optString("name","短信警告"),InputType.TYPE_CLASS_TEXT);
        text(form,"触发来源",15);
        Spinner source = new Spinner(this);
        source.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"普通短信 SMS 广播", "小米短信应用通知（SMS / RCS / 网络消息）"}));
        source.setSelection("notification".equals(old.optString("source")) ? 1 : 0); form.addView(source);
        text(form,"通知模式仅监听 com.android.mms。通知需显示发送人及消息内容；与普通 SMS 模式分开配置，避免同一条消息双重触发。",14);
        EditText senderNames = field(form,"通知显示的联系人姓名（精确匹配，多个用换行分隔；通知只显示姓名时必填）",old.optString("senderNames"),InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        contactNameTarget = senderNames;
        EditText number = field(form,"发送号码（多个号码用换行、逗号或分号分隔，任意一个匹配）",Store.numberText(old),InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        number.setMinLines(2);
        contactTarget = number;
        button(form,"从通讯录选择多个联系人/号码", () -> chooseContacts(number));
        EditText keyword = field(form,"消息包含关键词（留空则不限）",old.optString("keyword"),InputType.TYPE_CLASS_TEXT);
        text(form,"播放及调节的音频通道",15); Spinner channel = new Spinner(this); channel.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,CHANNELS)); channel.setSelection(Math.max(0,Math.min(3,old.optInt("channel")))); form.addView(channel);
        text(form,"音量百分比",15); SeekBar volume = new SeekBar(this); volume.setMax(100); volume.setProgress(old.optInt("volume",80)); form.addView(volume);
        TextView value = text(form,volume.getProgress()+"%",15);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean user) { value.setText(p+"%"); }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
        EditText duration = field(form,"最长播放秒数（5～600，循环播放至确认或超时）",String.valueOf(old.optInt("duration",60)),InputType.TYPE_CLASS_NUMBER);
        EditText message = field(form,"警告框内容",old.optString("message","收到匹配短信，请及时处理"),InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        selectedSound = old.optString("sound",""); soundLabel = text(form,selectedSound.isEmpty() ? "音源：默认系统闹钟" : "音源："+selectedSound,13);
        button(form,"选择系统铃声", () -> {
            Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);
            launch(picker,10);
        });
        button(form,"选择本地音频文件", () -> launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),11));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(index < 0 ? "新建短信宏" : "编辑短信宏").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnDismissListener(d -> { if (contactTarget == number) { contactTarget = null; contactNameTarget = null; } });
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            LinkedHashSet<String> numbers = Store.parseNumbers(number.getText().toString());
            boolean notification = source.getSelectedItemPosition() == 1;
            if (name.getText().toString().trim().isEmpty() || (numbers.isEmpty() && (!notification || Store.parseNames(senderNames.getText().toString()).isEmpty()))) {
                toast("请填写宏名称及发送号码；通知模式也可只填通知显示的联系人姓名"); return;
            }
            int seconds; try { seconds = Integer.parseInt(duration.getText().toString()); } catch (NumberFormatException e) { seconds = 0; }
            if (seconds < 5 || seconds > 600) { toast("播放时长应为 5～600 秒"); return; }
            try {
                JSONObject r = new JSONObject().put("source",notification ? "notification" : "sms").put("senderNames",String.join("\n",Store.parseNames(senderNames.getText().toString()))).put("name",name.getText().toString().trim()).put("numbers",new JSONArray(numbers)).put("keyword",keyword.getText().toString())
                    .put("channel",channel.getSelectedItemPosition()).put("volume",volume.getProgress()).put("duration",seconds)
                    .put("message",message.getText().toString()).put("sound",selectedSound).put("enabled",old.optBoolean("enabled",true));
                if (index < 0) rules.put(r); else rules.put(index,r);
                Store.save(this,rules); dialog.dismiss(); render();
            } catch (JSONException e) { toast(e.getMessage()); }
        })); dialog.show();
    }
    private void chooseContacts(EditText target) {
        if (contactTarget != target) return;
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            new AlertDialog.Builder(this).setTitle("读取通讯录")
                .setMessage("用于显示联系人姓名和电话号码，供你多选。只把选中的号码保存到宏中，不上传通讯录；也可以取消并手动输入号码。")
                .setPositiveButton("继续授权", (d,w) -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},3))
                .setNegativeButton("取消",null).show();
            return;
        }
        toast("正在读取联系人…");
        new Thread(() -> {
            LinkedHashMap<String,String> contacts = new LinkedHashMap<>();
            LinkedHashMap<String,String> names = new LinkedHashMap<>();
            try (Cursor cursor = getContentResolver().query(Phone.CONTENT_URI,
                    new String[]{Phone.DISPLAY_NAME, Phone.NUMBER},null,null,Phone.DISPLAY_NAME + " ASC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    String raw = cursor.getString(1);
                    if (raw == null) continue;
                    String n = Store.normalize(raw);
                    if (!n.isEmpty() && !contacts.containsKey(n)) {
                        String displayName = cursor.getString(0);
                        contacts.put(n, (displayName == null ? "未命名" : displayName) + " · " + raw);
                        if (displayName != null && !displayName.trim().isEmpty()) names.put(n,displayName);
                    }
                }
            } catch (RuntimeException e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) toast("读取联系人失败，可手动输入号码：" + e.getMessage()); });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || contactTarget != target) return;
                if (contacts.isEmpty()) { toast("没有可用的联系人号码，请手动输入"); return; }
                showContacts(target,contacts,names);
            });
        },"contact-reader").start();
    }
    private void showContacts(EditText target, LinkedHashMap<String,String> contacts, LinkedHashMap<String,String> names) {
        LinkedHashSet<String> selected = Store.parseNumbers(target.getText().toString());
        ArrayList<String> visible = new ArrayList<>();
        LinearLayout box = column();
        text(box,"联系人有多个号码时可分别勾选；已有手填号码会保留，可在编辑框删除。",14);
        EditText search = field(box,"搜索姓名或号码","",InputType.TYPE_CLASS_TEXT);
        ListView list = new ListView(this); list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        box.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(int)(320 * getResources().getDisplayMetrics().density)));
        Runnable refresh = () -> {
            String query = search.getText().toString().toLowerCase(Locale.ROOT).trim();
            visible.clear(); ArrayList<String> labels = new ArrayList<>();
            for (Map.Entry<String,String> entry : contacts.entrySet()) {
                if (entry.getValue().toLowerCase(Locale.ROOT).contains(query) || entry.getKey().contains(query)) {
                    visible.add(entry.getKey()); labels.add(entry.getValue());
                }
            }
            list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,labels));
            list.clearChoices();
            for (int i = 0; i < visible.size(); i++) list.setItemChecked(i,selected.contains(visible.get(i)));
        };
        list.setOnItemClickListener((parent,view,position,id) -> {
            String n = visible.get(position);
            if (list.isItemChecked(position)) selected.add(n); else selected.remove(n);
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { refresh.run(); }
            public void afterTextChanged(Editable e) {}
        });
        refresh.run();
        new AlertDialog.Builder(this).setTitle("多选联系人号码").setView(box)
            .setPositiveButton("使用所选号码", (d,w) -> {
                if (contactTarget != target) return;
                target.setText(String.join("\n",selected));
                if (contactNameTarget != null) {
                    LinkedHashSet<String> aliases = Store.parseNames(contactNameTarget.getText().toString());
                    aliases.removeAll(names.values());
                    for (String phone : selected) if (names.containsKey(phone)) aliases.add(names.get(phone));
                    contactNameTarget.setText(String.join("\n",aliases));
                }
            })
            .setNegativeButton("取消",null).show();
    }
    private void launch(Intent intent,int code) { try { startActivityForResult(intent,code); } catch (RuntimeException e) { toast("系统不支持此页面："+e.getMessage()); } }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null) return;
        Uri uri = request == 10 ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) : data.getData();
        if ((request == 10 || request == 11) && uri != null) {
            if (request == 11) try { getContentResolver().takePersistableUriPermission(uri,data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException e) { toast("音源访问未能持久保存，请重新选择文件"); return; }
            selectedSound = uri.toString(); if (soundLabel != null) soundLabel.setText("音源："+selectedSound);
        }
    }
    private void permissions() {
        LinearLayout box = column();
        text(box,"短信仅用于本机匹配，不上传、不读取历史短信。请逐项授权后开启监听。",16);
        button(box,(granted(Manifest.permission.RECEIVE_SMS)?"✓ ":"○ ")+"接收短信权限", () -> requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},1));
        button(box,(granted(Manifest.permission.READ_CONTACTS)?"✓ ":"○ ")+"通讯录权限（可选，手填号码无需授权）", () -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},3));
        if (Build.VERSION.SDK_INT >= 33) button(box,(granted(Manifest.permission.POST_NOTIFICATIONS)?"✓ ":"○ ")+"通知权限", () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2));
        button(box,(Settings.canDrawOverlays(this)?"✓ ":"○ ")+"允许悬浮警告框", () -> launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),20));
        button(box,(notificationAccess()?"✓ ":"○ ")+"短信应用通知访问（RCS / 网络消息）", () -> new AlertDialog.Builder(this)
            .setTitle("允许读取通知")
            .setMessage("Android 会授予通知访问能力；AutoDroid 只处理小米短信应用 com.android.mms 的消息通知，在本机匹配发送人及关键词，不上传、不保存正文。授权后还需把宏来源改为短信应用通知。")
            .setPositiveButton("前往授权", (d,w) -> launch(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),20)).setNegativeButton("取消",null).show());
        NotificationManager nm = getSystemService(NotificationManager.class);
        button(box,(nm.isNotificationPolicyAccessGranted()?"✓ ":"○ ")+"勿扰访问（铃声/通知音量受限时）", () -> launch(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),20));
        boolean exempt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        button(box,(exempt?"✓ ":"○ ")+"电池优化设置", () -> launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),20));
        button(box,"应用设置 / 权限被拒绝后手动开启", () -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),20));
        text(box,"部分手机还需手动允许自启动和后台运行。强行停止后需重新打开应用。锁屏、省电、勿扰、通话及系统敏感短信限制可能影响触发、声音或弹窗。音量修改会保留，不会自动恢复。",14);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("权限与后台设置（重新打开可刷新状态）").setView(scroll).setPositiveButton("关闭",null).show();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) { super.onRequestPermissionsResult(r,p,g); toast(g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED ? "授权成功" : "权限未授予，可在应用设置中开启"); render(); if (r == 3 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED && contactTarget != null) chooseContacts(contactTarget); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
