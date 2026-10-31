package com.autodroid;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if ((Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
                && Store.prefs(c).getBoolean("monitor", false)) {
            try {
                c.startForegroundService(new Intent(c, MacroService.class));
            } catch (RuntimeException e) {
                Store.log(c, c.getString(R.string.log_boot_restore_failed) + e.getMessage());
            }
        }
    }
}
