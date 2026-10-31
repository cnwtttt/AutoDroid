package com.autodroid;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;

import java.text.SimpleDateFormat;
import java.util.*;

/** Explicit user-requested read-only query. Message data is never persisted or logged. */
final class RecentSmsDiagnostics {
    static final class Result {
        final int count;
        final String text;

        Result(int count, String text) {
            this.count = count;
            this.text = text;
        }
    }

    static Result read(Context context) {
        String[] columns = {"_id", "address", "body", "date", "type"};
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "date DESC, _id DESC");
        args.putInt(ContentResolver.QUERY_ARG_LIMIT, 20);
        StringBuilder report = new StringBuilder(context.getString(R.string.inbox_report_intro));
        int count = 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        try (Cursor cursor =
                context.getContentResolver()
                        .query(Telephony.Sms.Inbox.CONTENT_URI, columns, args, null)) {
            if (cursor == null)
                throw new IllegalStateException(context.getString(R.string.inbox_null_cursor));
            while (count < 20 && cursor.moveToNext()) {
                count++;
                String body = cursor.getString(2);
                if (body == null) body = "";
                report.append(
                        context.getString(
                                R.string.inbox_record,
                                count,
                                cursor.getLong(0),
                                cursor.getInt(4),
                                format.format(new Date(cursor.getLong(3))),
                                cursor.getString(1),
                                body.substring(0, Math.min(512, body.length()))));
                if (body.length() > 512)
                    report.append(context.getString(R.string.inbox_body_truncated));
                report.append("\n\n");
            }
        }
        if (count == 0) report.append(context.getString(R.string.inbox_report_empty));
        report.append(context.getString(R.string.inbox_report_footer));
        return new Result(count, report.toString());
    }
}
