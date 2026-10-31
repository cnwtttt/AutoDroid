package com.autodroid;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.content.ContentResolver;
import android.provider.Telephony;
import java.text.SimpleDateFormat;
import java.util.*;

/** Explicit user-requested read-only query. Message data is never persisted or logged. */
final class RecentSmsDiagnostics {
    static final class Result {
        final int count;
        final String text;
        Result(int count,String text) { this.count=count; this.text=text; }
    }
    static Result read(Context context) {
        String[] columns = {"_id","address","body","date","type"};
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"date DESC, _id DESC");
        args.putInt(ContentResolver.QUERY_ARG_LIMIT,20);
        StringBuilder report = new StringBuilder("查询：content://sms/inbox\n按时间倒序，最多20条，仅查看，不触发宏。\n\n");
        int count=0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
        try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,columns,args,null)) {
            if (cursor == null) throw new IllegalStateException("短信Provider返回空Cursor，无法确认查询结果");
            while (count < 20 && cursor.moveToNext()) {
                count++;
                String body = cursor.getString(2);
                if (body == null) body="";
                report.append("第").append(count).append("条 · ID=").append(cursor.getLong(0))
                    .append(" · type=").append(cursor.getInt(4)).append('\n')
                    .append("时间：").append(format.format(new Date(cursor.getLong(3)))).append('\n')
                    .append("发送号码：").append(cursor.getString(1)).append('\n')
                    .append("正文：").append(body.substring(0,Math.min(512,body.length())));
                if (body.length()>512) report.append("…（仅展示前512字符）");
                report.append("\n\n");
            }
        }
        if (count==0) report.append("没有可读取的收件箱记录。可能确实没有记录，也可能被系统隐私/权限策略过滤。\n");
        report.append("若测试的RCS/网络消息不在这里，不能据此读取其内容：它可能位于独立Provider，或受到系统过滤。\n结果不保存到执行日志、备份或logcat。");
        return new Result(count,report.toString());
    }
}
