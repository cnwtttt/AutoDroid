package com.autodroid;

import android.app.*;
import android.content.Context;
import android.os.*;
import android.service.notification.StatusBarNotification;

import java.lang.reflect.Array;
import java.util.*;

/** Bounded public-field inspection; never invokes PendingIntents or decodes private objects. */
final class NotificationDiagnostics {
    private final Context context;
    private final boolean values;
    private int lines;

    private NotificationDiagnostics(Context context, boolean values) {
        this.context = context;
        this.values = values;
    }

    static void dump(Context c, StatusBarNotification sbn) {
        boolean values = Store.prefs(c).getBoolean("notificationFieldValues", false);
        if (!values && !Store.prefs(c).getBoolean("notificationFieldDiagnostics", false)) return;
        NotificationDiagnostics d = new NotificationDiagnostics(c, values);
        Notification n = sbn.getNotification();
        d.line(
                "NOTIFICATION_DUMP_BEGIN id="
                        + sbn.getId()
                        + " channel="
                        + n.getChannelId()
                        + " flags=0x"
                        + Integer.toHexString(n.flags)
                        + " category="
                        + n.category
                        + " visibility="
                        + n.visibility
                        + " postTime="
                        + sbn.getPostTime()
                        + " when="
                        + n.when
                        + " groupSummary="
                        + ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
                        + " ongoing="
                        + sbn.isOngoing()
                        + " values="
                        + values);
        d.line(
                "NOTIFICATION_DUMP_META tagPresent="
                        + (sbn.getTag() != null)
                        + " groupPresent="
                        + (n.getGroup() != null)
                        + " contentIntent="
                        + (n.contentIntent != null)
                        + " fullScreenIntent="
                        + (n.fullScreenIntent != null)
                        + " actions="
                        + (n.actions == null ? 0 : n.actions.length)
                        + " customViews="
                        + (n.contentView != null)
                        + " customBigViews="
                        + (n.bigContentView != null));
        d.inspect("extras", n.extras, 0);
        if (n.publicVersion != null) d.inspect("publicVersion.extras", n.publicVersion.extras, 0);
        Store.log(
                c,
                "NOTIFICATION_DUMP_END id="
                        + sbn.getId()
                        + " lines="
                        + d.lines
                        + c.getString(R.string.log_notificationdiagnostics_0));
    }

    private void line(String s) {
        if (lines++ < 80) Store.log(context, s);
    }

    private String safe(String s) {
        return s.substring(0, Math.min(256, s.length()))
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void inspect(String path, Object value, int depth) {
        if (lines >= 80) return;
        if (value == null) {
            line("NOTIFICATION_FIELD " + path + " type=null");
            return;
        }
        String info = "NOTIFICATION_FIELD " + path + " type=" + value.getClass().getName();
        if (value instanceof CharSequence) {
            String text = value.toString();
            line(
                    info
                            + " length="
                            + text.length()
                            + (values ? " value=" + safe(text) : " value=<hidden>"));
        } else if (value instanceof Bundle) {
            try {
                Bundle bundle = (Bundle) value;
                ArrayList<String> keys = new ArrayList<>(bundle.keySet());
                Collections.sort(keys);
                line(info + " fields=" + keys.size());
                if (depth >= 4) return;
                int count = 0;
                for (String key : keys) {
                    if (count++ >= 16) {
                        line("NOTIFICATION_FIELD " + path + " <remaining fields truncated>");
                        break;
                    }
                    try {
                        inspect(path + "." + safe(key), bundle.get(key), depth + 1);
                    } catch (RuntimeException e) {
                        line(
                                "NOTIFICATION_FIELD "
                                        + path
                                        + "."
                                        + safe(key)
                                        + " unreadable="
                                        + e.getClass().getSimpleName());
                    }
                }
            } catch (RuntimeException e) {
                line(info + " unreadable=" + e.getClass().getSimpleName());
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            line(info + " length=" + length);
            if (depth < 4 && !(value instanceof byte[]))
                for (int i = 0; i < Math.min(16, length); i++)
                    inspect(path + "[" + i + "]", Array.get(value, i), depth + 1);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            line(info + " length=" + list.size());
            if (depth < 4)
                for (int i = 0; i < Math.min(16, list.size()); i++)
                    inspect(path + "[" + i + "]", list.get(i), depth + 1);
        } else if (Build.VERSION.SDK_INT >= 28 && value instanceof Person) {
            line(info);
            if (depth < 4) {
                Person person = (Person) value;
                inspect(path + ".name", person.getName(), depth + 1);
                inspect(path + ".uri", person.getUri(), depth + 1);
            }
        } else if (value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            line(info + (values ? " value=" + safe(String.valueOf(value)) : " value=<hidden>"));
        } else {
            line(info + " value=<opaque>");
        }
    }
}
