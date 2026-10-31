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
    private MaterialUi ui;
    private int selectedPage;
    private int renderedPage;
    private ScrollView activeScroll;
    private final int[] scrollPositions=new int[3];
    private boolean readingRecentSms;
    private EditText contactTarget;
    private String selectedSound = "";
    private TextView soundLabel;
    private static final String[] CHANNELS = {"闹钟", "媒体", "铃声", "通知"};
    @Override public void onCreate(Bundle state) { super.onCreate(state);
        ui=new MaterialUi(this);
        if (state!=null) selectedPage=state.getInt("page",0);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::backgroundFromBack);
        }
        JSONArray rules = Store.rules(this);
        if (Store.migrateToSms(rules)) { Store.save(this,rules); Store.log(this,"通知触发已停用，原宏已转换为普通短信；无号码的宏已禁用，请编辑补充号码"); }
        Store.prefs(this).edit().putBoolean("senderDiagnostics",false).putBoolean("notificationFieldDiagnostics",false).putBoolean("notificationFieldValues",false).apply();
        render(); }
    @Override public void onBackPressed() { backgroundFromBack(); }
    private void backgroundFromBack() {
        boolean moved=moveTaskToBack(true);
        Store.log(this,"APP_BACK_BACKGROUND moved="+moved+" monitor="+Store.prefs(this).getBoolean("monitor",false));
    }
    @Override public void onResume() {
        super.onResume(); render();
        Store.log(this, "APP_RESUME version=0.5.1 monitor=" + Store.prefs(this).getBoolean("monitor", false)
            + " readSmsPermission=" + granted(Manifest.permission.READ_SMS)
            + " receiveSmsPermission=" + granted(Manifest.permission.RECEIVE_SMS)
            + " overlayPermission=" + Settings.canDrawOverlays(this));
        for (ActivityManager.AppTask task : getSystemService(ActivityManager.class).getAppTasks()) task.setExcludeFromRecents(true);
        if (Store.prefs(this).getBoolean("monitor",false) && !MacroService.isRunning) {
            try { Store.log(this,"MONITOR_RESTORE 恢复前台服务"); startForegroundService(new Intent(this,MacroService.class)); }
            catch (RuntimeException e) { Store.log(this,"MONITOR_RESTORE_FAILED " + e.getMessage()); }
        }
    }
    @Override public void onStop() {
        Store.log(this,"APP_ACTIVITY_STOP monitor="+Store.prefs(this).getBoolean("monitor",false)+" serviceRunning="+MacroService.isRunning);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("page",selectedPage); super.onSaveInstanceState(state); }
    private LinearLayout column() { return ui.column(20); }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView t=ui.label(value,size,size>=20?ui.ink:ui.muted,size>=20);
        t.setPadding(0,ui.dp(6),0,ui.dp(6)); parent.addView(t); return t;
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button b=ui.button(label,false,action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=ui.dp(8); parent.addView(b,lp);
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
        if (activeScroll!=null) scrollPositions[renderedPage]=activeScroll.getScrollY();
        renderedPage=selectedPage;
        LinearLayout shell=ui.column(0); shell.setBackgroundColor(ui.background);
        setContentView(shell);
        shell.setOnApplyWindowInsetsListener((view,insets) -> {
            if (Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        shell.requestApplyInsets();
        getWindow().setStatusBarColor(ui.background); getWindow().setNavigationBarColor(ui.container);
        if (Build.VERSION.SDK_INT>=30) getWindow().getInsetsController().setSystemBarsAppearance(
            ui.background==0xFFF8F9FF ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout bar=ui.row(); bar.setPadding(ui.dp(24),ui.dp(16),ui.dp(24),ui.dp(12));
        TextView brand=ui.label("AutoDroid",22,ui.ink,true); bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=ui.label("本机运行",12,ui.onAccent,false); local.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6)); local.setBackground(ui.shape(ui.accent,16)); bar.addView(local); shell.addView(bar);
        FrameLayout body=new FrameLayout(this); shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        activeScroll=scroll; final int restoreY=scrollPositions[selectedPage]; scroll.post(() -> scroll.scrollTo(0,restoreY));
        body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout wrapper=ui.column(0); wrapper.setGravity(Gravity.CENTER_HORIZONTAL); scroll.addView(wrapper);
        root=column(); root.setPadding(ui.dp(24),ui.dp(8),ui.dp(24),ui.dp(selectedPage==0?104:24));
        int width=getResources().getConfiguration().screenWidthDp;
        wrapper.addView(root,new LinearLayout.LayoutParams(width>640?ui.dp(640):-1,-2));
        if (selectedPage==0) renderMacros(); else if (selectedPage==1) renderLogs(); else renderSettings();
        if (selectedPage==0) {
            Button fab=ui.button("新建宏",true,() -> edit(-1)); fab.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
            android.graphics.drawable.Drawable plus=ui.icon("add",ui.onPrimary).getDrawable(); plus.setBounds(0,0,ui.dp(24),ui.dp(24)); fab.setCompoundDrawables(plus,null,null,null); fab.setCompoundDrawablePadding(ui.dp(8)); fab.setElevation(ui.dp(4)); fab.setMinHeight(ui.dp(56)); fab.setBackground(ui.ripple(ui.primary,16));
            FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END); fp.setMargins(ui.dp(24),0,ui.dp(24),ui.dp(20)); body.addView(fab,fp);
        }
        LinearLayout nav=ui.row(); nav.setBaselineAligned(false); nav.setBackgroundColor(ui.container); nav.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
        String[] labels={"自动化","日志","设置"}, icons={"macros","logs","settings"};
        for (int i=0;i<3;i++) {
            final int page=i; boolean active=i==selectedPage;
            LinearLayout item=ui.column(4); item.setGravity(Gravity.CENTER); item.setMinimumHeight(ui.dp(64));
            FrameLayout pill=new FrameLayout(this); pill.setBackground(ui.shape(active?ui.accent:ui.container,20));
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER); pill.addView(ui.icon(icons[i],active?ui.onAccent:ui.muted),ip);
            LinearLayout.LayoutParams pillParams=new LinearLayout.LayoutParams(ui.dp(64),ui.dp(32)); pillParams.gravity=Gravity.CENTER_HORIZONTAL; item.addView(pill,pillParams);
            TextView label=ui.label(labels[i],12,active?ui.ink:ui.muted,active);
            label.setGravity(Gravity.CENTER); label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); label.setSingleLine(true); label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,-2); labelParams.topMargin=ui.dp(4); item.addView(label,labelParams);
            item.setBackground(ui.ripple(ui.container,20)); item.setSelected(active); item.setContentDescription(labels[i]+(active?"，已选中":"")); item.setOnClickListener(v -> { selectedPage=page; render(); });
            nav.addView(item,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1));
        }
        shell.addView(nav,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ui.dp(80)));
    }
    private void toggleMonitor(boolean enabled) {
        if (!enabled && !granted(Manifest.permission.READ_SMS)) { requestPermissions(new String[]{Manifest.permission.READ_SMS},5); return; }
        if (!enabled && needsSms() && !granted(Manifest.permission.RECEIVE_SMS)) { requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},1); return; }
        Store.log(this,"MONITOR_REQUEST enabled="+!enabled);
        if (enabled) {
            Store.prefs(this).edit().putBoolean("monitor",false).commit();
            startService(new Intent(this,MacroService.class).setAction("STOP"));
        } else {
            Store.prefs(this).edit().putBoolean("monitor",true).putLong("monitorSince",System.currentTimeMillis()).commit();
            try { startForegroundService(new Intent(this,MacroService.class)); }
            catch (RuntimeException e) { Store.prefs(this).edit().putBoolean("monitor",false).commit(); Store.log(this,"MONITOR_START_FAILED "+e.getMessage()); toast(e.getMessage()); }
        }
        render();
    }
    private void renderMacros() {
        text(root,"让重要消息被听见",28);
        text(root,"为指定短信设置专属声音与提醒。",14); ui.space(root,16);
        boolean enabled=Store.prefs(this).getBoolean("monitor",false);
        LinearLayout status=ui.card(root,ui.accent);
        LinearLayout row=ui.row(); row.addView(ui.icon("message",ui.onAccent),new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));
        TextView title=ui.label(enabled?"短信监听已开启":"短信监听已暂停",20,ui.onAccent,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=ui.dp(12); row.addView(title,tp); status.addView(row);
        text(status,enabled?"匹配到新消息时，播放声音并显示重要警告。":"开启后，自动为符合条件的新消息发出警告。",14);
        Button control=ui.button(enabled?"暂停监听":"开启监听",true,() -> toggleMonitor(enabled));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=ui.dp(12); status.addView(control,cp);
        if (!granted(Manifest.permission.READ_SMS) || !Settings.canDrawOverlays(this) || (Build.VERSION.SDK_INT>=33 && !granted(Manifest.permission.POST_NOTIFICATIONS))) {
            LinearLayout notice=ui.card(root,ui.container); text(notice,"完善提醒权限",18); text(notice,"检查短信读取、通知与悬浮窗权限，让提醒完整呈现。",14); button(notice,"检查权限",this::permissions);
        }
        JSONArray rules=Store.rules(this); int enabledCount=0;
        for(int i=0;i<rules.length();i++) if(rules.optJSONObject(i)!=null && rules.optJSONObject(i).optBoolean("enabled",true)) enabledCount++;
        text(root,"我的宏",22); text(root,rules.length()+" 条自动化 · "+enabledCount+" 条已启用",13); ui.space(root,12);
        if (rules.length()==0) {
            LinearLayout empty=ui.card(root,ui.surface); empty.setGravity(Gravity.CENTER);
            empty.addView(ui.icon("macros",ui.primary),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
            text(empty,"创建第一条自动化",20); text(empty,"选择发送号码、填写关键词，再设置你想听到的声音。",14); button(empty,"创建短信宏",() -> edit(-1));
        }
        for(int i=0;i<rules.length();i++) {
            JSONObject r=rules.optJSONObject(i); if(r==null) continue; final int index=i;
            LinearLayout card=ui.card(root,ui.surface); LinearLayout header=ui.row();
            TextView name=ui.label(r.optString("name"),20,ui.ink,true); header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Switch toggle=new Switch(this); toggle.setChecked(r.optBoolean("enabled",true)); toggle.setContentDescription(r.optString("name")+"，启用宏"); toggle.setMinimumWidth(ui.dp(56)); toggle.setMinimumHeight(ui.dp(48));
            toggle.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ui.primary,ui.muted}));
            header.addView(toggle); card.addView(header);
            toggle.setOnCheckedChangeListener((v,checked) -> { try { r.put("enabled",checked); Store.save(this,rules); render(); } catch(JSONException e) { toast(e.getMessage()); } });
            String numbers=String.join("、",Store.numbers(r)); TextView sender=text(card,"来自  "+numbers,14); sender.setMaxLines(2); sender.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text(card,r.optString("keyword").isEmpty()?"匹配所有内容":"包含  “"+r.optString("keyword")+"”",14);
            ui.space(card,8); TextView chip=ui.label(CHANNELS[Math.max(0,Math.min(3,r.optInt("channel")))]+"  ·  "+r.optInt("volume")+"%  ·  "+r.optInt("duration")+" 秒",12,ui.onAccent,false);
            chip.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)); chip.setBackground(ui.shape(ui.accent,12)); card.addView(chip); ui.space(card,16);
            LinearLayout actions=ui.row(); Button test=ui.button("测试",false,() -> testRule(r)); actions.addView(test,new LinearLayout.LayoutParams(0,-2,1));
            Button edit=ui.button("编辑",false,() -> edit(index)); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,-2,1); ep.leftMargin=ui.dp(8); actions.addView(edit,ep);
            Button more=ui.button("⋮",false,() -> new AlertDialog.Builder(this).setTitle(r.optString("name")).setItems(new String[]{"删除这条宏"},(d,w) -> new AlertDialog.Builder(this).setTitle("删除宏？").setMessage("删除后无法撤销，可先在设置中导出备份。").setPositiveButton("删除",(a,b) -> { rules.remove(index); Store.save(this,rules); render(); }).setNegativeButton("取消",null).show()).show());
            more.setContentDescription("更多操作："+r.optString("name")); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(ui.dp(48),-2); mp.leftMargin=ui.dp(8); more.setPadding(0,0,0,0); actions.addView(more,mp); card.addView(actions);
        }
        button(root,"停止当前警告",() -> startService(new Intent(this,MacroService.class).setAction("SILENCE")));
    }
    private void testRule(JSONObject r) {
        new AlertDialog.Builder(this).setTitle("测试这条宏？").setMessage("将立即调节系统音量、播放声音并弹出警告。测试会跳过短信匹配条件。")
            .setPositiveButton("开始测试",(d,w) -> { try { startForegroundService(new Intent(this,MacroService.class).setAction("RUN").putExtra("rule",r.toString()).putExtra("source","手动测试")); } catch(RuntimeException e) { toast(e.getMessage()); } }).setNegativeButton("取消",null).show();
    }
    private void renderLogs() {
        text(root,"执行日志",28); text(root,"查看监听与宏的运行记录，不记录短信正文。",14); ui.space(root,16);
        LinearLayout actions=ui.row(); actions.addView(ui.button("刷新",false,this::render),new LinearLayout.LayoutParams(0,-2,1));
        Button clear=ui.button("清空日志",false,() -> new AlertDialog.Builder(this).setTitle("清空执行日志？").setPositiveButton("清空",(d,w) -> { Store.prefs(this).edit().remove("logs").apply(); render(); }).setNegativeButton("取消",null).show());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.leftMargin=ui.dp(8); actions.addView(clear,lp); root.addView(actions); ui.space(root,16);
        LinearLayout card=ui.card(root,ui.surface); TextView logs=text(card,Store.prefs(this).getString("logs","暂无记录。开启监听或测试宏后，运行记录会显示在这里。"),13); logs.setTextIsSelectable(true);
    }
    private void renderSettings() {
        text(root,"设置",28); text(root,"管理权限、备份与本机诊断。",14); ui.space(root,16);
        LinearLayout permissions=ui.card(root,ui.surface); text(permissions,"权限与后台",20); text(permissions,"短信、通知、悬浮窗与省电设置",14); button(permissions,"管理权限",this::permissions);
        LinearLayout backup=ui.card(root,ui.surface); text(backup,"宏备份",20); text(backup,"换机或重装前，先保存你的自动化配置。",14);
        button(backup,"导出宏",() -> launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"AutoDroid-macros-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json"),30));
        button(backup,"从备份恢复",() -> launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),31));
        LinearLayout diagnose=ui.card(root,ui.surface); text(diagnose,"诊断工具",20); text(diagnose,"排查消息读取问题，仅在你主动操作时展示最近消息。",14); button(diagnose,"查看最近消息",this::recentSmsDiagnostics);
        LinearLayout about=ui.card(root,ui.container); text(about,"只在你的手机上运行",18); text(about,"应用无联网权限，消息在本机匹配。按返回键可退到后台继续监听。",14); text(about,"AutoDroid  0.5.1",12);
    }
    private EditText field(LinearLayout box, String label, String value, int type) {
        text(box,label,14); EditText e=new EditText(this); e.setInputType(type); e.setText(value); e.setTextColor(ui.ink); e.setTextSize(16);
        e.setHintTextColor(ui.muted); e.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));
        android.graphics.drawable.GradientDrawable bg=ui.shape(ui.container,12); bg.setStroke(ui.dp(1),ui.outline); e.setBackground(bg); e.setMinHeight(ui.dp(56));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=ui.dp(12); box.addView(e,lp); return e;
    }
    private void edit(int index) {
        JSONArray rules = Store.rules(this); JSONObject old = index < 0 ? new JSONObject() : rules.optJSONObject(index);
        LinearLayout form = column(); ScrollView scroll = new ScrollView(this); scroll.addView(form);
        text(form,"基本信息",22);
        EditText name = field(form,"宏名称",old.optString("name","短信警告"),InputType.TYPE_CLASS_TEXT);
        text(form,"触发条件",22);
        text(form,"号码满足其一，并包含下方关键词时触发。",14);
        EditText number = field(form,"发送号码（多个号码用换行、逗号或分号分隔，任意一个匹配）",Store.numberText(old),InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        number.setMinLines(2);
        contactTarget = number;
        button(form,"从通讯录选择多个联系人/号码", () -> chooseContacts(number));
        EditText keyword = field(form,"消息包含关键词（留空则不限）",old.optString("keyword"),InputType.TYPE_CLASS_TEXT);
        text(form,"声音与音量",22);
        text(form,"播放及调节的音频通道",15); Spinner channel = new Spinner(this); channel.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,CHANNELS)); channel.setMinimumHeight(ui.dp(48)); channel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.primary)); channel.setSelection(Math.max(0,Math.min(3,old.optInt("channel")))); form.addView(channel);
        text(form,"音量百分比",15); SeekBar volume = new SeekBar(this); volume.setMinimumHeight(ui.dp(48)); volume.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.primary)); volume.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.primary)); volume.setMax(100); volume.setProgress(old.optInt("volume",80)); form.addView(volume);
        TextView value = text(form,volume.getProgress()+"%",15);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean user) { value.setText(p+"%"); }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
        EditText duration = field(form,"最长播放秒数（5～600，循环播放至确认或超时）",String.valueOf(old.optInt("duration",60)),InputType.TYPE_CLASS_NUMBER);
        text(form,"警告内容",22);
        EditText message = field(form,"警告框内容",old.optString("message","收到匹配短信，请及时处理"),InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        selectedSound = old.optString("sound",""); soundLabel = text(form,selectedSound.isEmpty() ? "音源：默认系统闹钟" : "音源：已选择自定义音源",13);
        button(form,"选择系统铃声", () -> {
            Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);
            launch(picker,10);
        });
        button(form,"选择本地音频文件", () -> launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),11));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(index < 0 ? "新建短信宏" : "编辑短信宏").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnDismissListener(d -> { if (contactTarget == number) { contactTarget = null; } });
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            LinkedHashSet<String> numbers = Store.parseNumbers(number.getText().toString());
            if (name.getText().toString().trim().isEmpty() || numbers.isEmpty()) {
                toast("请填写宏名称及发送号码"); return;
            }
            int seconds; try { seconds = Integer.parseInt(duration.getText().toString()); } catch (NumberFormatException e) { seconds = 0; }
            if (seconds < 5 || seconds > 600) { toast("播放时长应为 5～600 秒"); return; }
            try {
                JSONObject r = new JSONObject().put("source","sms").put("name",name.getText().toString().trim()).put("numbers",new JSONArray(numbers)).put("keyword",keyword.getText().toString())
                    .put("channel",channel.getSelectedItemPosition()).put("volume",volume.getProgress()).put("duration",seconds)
                    .put("message",message.getText().toString()).put("sound",selectedSound).put("enabled",old.optBoolean("enabled",true));
                if (index < 0) rules.put(r); else rules.put(index,r);
                Store.save(this,rules); dialog.dismiss(); render();
            } catch (JSONException e) { toast(e.getMessage()); }
        })); dialog.show();
        if (dialog.getWindow()!=null) dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*0.9f));
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

            })
            .setNegativeButton("取消",null).show();
    }
    private void launch(Intent intent,int code) { try { startActivityForResult(intent,code); } catch (RuntimeException e) { toast("系统不支持此页面："+e.getMessage()); } }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null) return;
        if ((request == 30 || request == 31) && data.getData() != null) { backupFile(request,data.getData()); return; }
        Uri uri = request == 10 ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) : data.getData();
        if ((request == 10 || request == 11) && uri != null) {
            if (request == 11) try { getContentResolver().takePersistableUriPermission(uri,data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException e) { toast("音源访问未能持久保存，请重新选择文件"); return; }
            selectedSound = uri.toString(); if (soundLabel != null) soundLabel.setText("音源：已选择自定义音源");
        }
    }
    private void backupFile(int request, Uri uri) {
        String snapshot = Store.rules(this).toString();
        toast(request == 30 ? "正在导出宏…" : "正在读取备份…");
        new Thread(() -> {
            try {
                if (request == 30) {
                    byte[] bytes = MacroBackup.exportRules(new JSONArray(snapshot)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (bytes.length > MacroBackup.MAX_BYTES) throw new java.io.IOException("备份超过1MB限制");
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri,"wt")) {
                        if (out == null) throw new java.io.IOException("无法打开目标文件");
                        out.write(bytes); out.flush();
                    }
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) toast("宏备份已导出，请确认文件已保存再卸载应用"); });
                } else {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new java.io.IOException("无法打开备份文件");
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (bytes.size()+count > MacroBackup.MAX_BYTES) throw new java.io.IOException("备份超过1MB限制");
                            bytes.write(buffer,0,count);
                        }
                    }
                    JSONArray imported = MacroBackup.importRules(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) confirmImport(imported); });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) toast("备份操作失败："+e.getMessage()); });
            }
        },"macro-backup").start();
    }
    private void confirmImport(JSONArray imported) {
        new AlertDialog.Builder(this).setTitle("恢复 "+imported.length()+" 条宏")
            .setMessage("追加会保留现有宏，重复导入也会追加。替换会覆盖全部现有宏。恢复后监听将关闭，请检查号码和音源，再手动开启。音频文件和系统权限不会随备份恢复。")
            .setPositiveButton("追加", (d,w) -> applyImport(imported,false))
            .setNeutralButton("替换全部", (d,w) -> new AlertDialog.Builder(this).setTitle("确认替换全部宏")
                .setMessage("将用备份中的 "+imported.length()+" 条宏替换当前全部宏，请先确认已导出当前配置。")
                .setPositiveButton("确认替换", (dialog,which) -> applyImport(imported,true)).setNegativeButton("取消",null).show())
            .setNegativeButton("取消",null).show();
    }
    private void applyImport(JSONArray imported, boolean replace) {
        JSONArray combined = replace ? new JSONArray() : Store.rules(this);
        if (combined.length()+imported.length()>500) { toast("恢复后宏数量不能超过500"); return; }
        for (int i=0;i<imported.length();i++) combined.put(imported.optJSONObject(i));
        boolean saved = Store.prefs(this).edit().putString("rules",combined.toString()).putBoolean("monitor",false).commit();
        if (!saved) { toast("保存失败，请检查存储空间"); return; }
        try { startService(new Intent(this,MacroService.class).setAction("STOP")); }
        catch (RuntimeException e) { Store.log(this,"恢复后停止服务失败："+e.getMessage()); }
        render(); toast("已恢复 "+imported.length()+" 条宏；请重新检查权限和音源后开启监听");
    }
    private void recentSmsDiagnostics() {
        if (readingRecentSms) { toast("正在读取，请稍候"); return; }
        if (!granted(Manifest.permission.READ_SMS)) {
            new AlertDialog.Builder(this).setTitle("读取最近消息诊断")
                .setMessage("需要读取短信权限，用于查看标准收件箱最近20条消息的号码、时间和正文，确认RCS是否也存入标准短信表。只在此页面展示，不保存、不写入logcat，不执行宏。")
                .setPositiveButton("申请权限",(d,w) -> requestPermissions(new String[]{Manifest.permission.READ_SMS},4))
                .setNegativeButton("取消",null).show();
            return;
        }
        readingRecentSms=true; toast("正在查询标准短信收件箱…");
        Context app = getApplicationContext();
        new Thread(() -> {
            try {
                RecentSmsDiagnostics.Result result = RecentSmsDiagnostics.read(app);
                Store.log(app,"RECENT_SMS_DIAGNOSTIC success rows="+result.count+"（号码与正文仅在诊断页面展示）");
                runOnUiThread(() -> {
                    readingRecentSms=false;
                    if (isFinishing() || isDestroyed()) return;
                    LinearLayout box=column();
                    TextView report=text(box,result.text,15); report.setTextIsSelectable(true);
                    ScrollView scroll=new ScrollView(this); scroll.addView(box);
                    new AlertDialog.Builder(this).setTitle("最近消息诊断 · "+result.count+" 条")
                        .setView(scroll).setPositiveButton("关闭",null)
                        .setNeutralButton("重新读取",(d,w) -> recentSmsDiagnostics()).show();
                });
            } catch (RuntimeException e) {
                Store.log(app,"RECENT_SMS_DIAGNOSTIC failed="+e.getClass().getSimpleName());
                runOnUiThread(() -> {
                    readingRecentSms=false;
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(this).setTitle("无法读取最近消息")
                        .setMessage(e.getClass().getSimpleName()+"："+e.getMessage()+"\n\n接收短信权限不等于读取短信权限；系统也可能限制第三方读取。请检查读取短信授权。")
                        .setPositiveButton("应用权限设置",(d,w) -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),20))
                        .setNegativeButton("关闭",null).show();
                });
            }
        },"recent-sms-diagnostic").start();
    }
    private void permissions() {
        LinearLayout box = column();
        text(box,"短信触发用于本机匹配，不上传；开启监听后读取新增收件箱记录以自动触发；最近消息诊断仅在你点击时展示。",16);
        button(box,(granted(Manifest.permission.RECEIVE_SMS)?"✓ ":"○ ")+"接收短信权限", () -> requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},1));
        button(box,(granted(Manifest.permission.READ_SMS)?"✓ ":"○ ")+"读取短信权限（数据库自动触发 / 最近消息诊断）",this::recentSmsDiagnostics);
        button(box,(granted(Manifest.permission.READ_CONTACTS)?"✓ ":"○ ")+"通讯录权限（可选，手填号码无需授权）", () -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},3));
        if (Build.VERSION.SDK_INT >= 33) button(box,(granted(Manifest.permission.POST_NOTIFICATIONS)?"✓ ":"○ ")+"通知权限", () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2));
        button(box,(Settings.canDrawOverlays(this)?"✓ ":"○ ")+"允许悬浮警告框", () -> launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),20));
        button(box,"重要警告通知设置（横幅 / 声音 / 锁屏）", () -> {
            MacroService.ensureAlertChannel(this);
            launch(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,"alerts"),20);
        });
        NotificationManager nm = getSystemService(NotificationManager.class);
        button(box,(nm.isNotificationPolicyAccessGranted()?"✓ ":"○ ")+"勿扰访问（铃声/通知音量受限时）", () -> launch(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),20));
        boolean exempt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        button(box,(exempt?"✓ ":"○ ")+"电池优化设置", () -> launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),20));
        button(box,"应用设置 / 权限被拒绝后手动开启", () -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),20));
        text(box,"部分手机还需手动允许自启动和后台运行。强行停止后需重新打开应用。锁屏、省电、勿扰、通话及系统敏感短信限制可能影响触发、声音或弹窗。音量修改会保留，不会自动恢复。",14);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("权限与后台设置（重新打开可刷新状态）").setView(scroll).setPositiveButton("关闭",null).show();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) { super.onRequestPermissionsResult(r,p,g); toast(g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED ? "授权成功" : "权限未授予，可在应用设置中开启"); render(); if (r == 3 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED && contactTarget != null) chooseContacts(contactTarget); if (r == 4 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) recentSmsDiagnostics();
        if ((r == 4 || r == 5) && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED && Store.prefs(this).getBoolean("monitor",false)) {
            try { startForegroundService(new Intent(this,MacroService.class).setAction("SCAN_INBOX")); } catch (RuntimeException e) { toast(e.getMessage()); }
        } }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
