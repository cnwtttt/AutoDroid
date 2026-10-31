package com.autodroid;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if ((Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) && Store.prefs(c).getBoolean("monitor", false)) {
            try { c.startForegroundService(new Intent(c, MacroService.class)); }
            catch (RuntimeException e) { Store.log(c, "开机恢复失败，请打开应用：" + e.getMessage()); }
        }
    }
}
