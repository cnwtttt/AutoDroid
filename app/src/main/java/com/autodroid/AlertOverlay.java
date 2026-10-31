package com.autodroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Service-owned alert surface; shared by both UIs without an Activity dependency. */
final class AlertOverlay {
    private final Context context;
    private final int surface, ink, muted, primary, onPrimary, outline;

    private AlertOverlay(Context context) {
        this.context = context;
        boolean dark =
                (context.getResources().getConfiguration().uiMode
                                & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        boolean x = !"play".equals(Store.prefs(context).getString("colorStyle", "x"));
        surface = dark ? (x ? 0xFF202327 : 0xFF28292A) : (x ? 0xFFF7F9F9 : 0xFFF8F9FA);
        ink = x ? (dark ? 0xFFE7E9EA : 0xFF0F1419) : (dark ? 0xFFE3E3E3 : 0xFF202124);
        muted = x ? (dark ? 0xFF8B98A5 : 0xFF536471) : (dark ? 0xFFBDC1C6 : 0xFF5F6368);
        primary = x ? (dark ? 0xFFE7E9EA : 0xFF0F1419) : (dark ? 0xFFA8C7FA : 0xFF0B57D0);
        onPrimary = x ? (dark ? 0xFF0F1419 : 0xFFFFFFFF) : (dark ? 0xFF062E6F : 0xFFFFFFFF);
        outline = x ? (dark ? 0xFF2F3336 : 0xFFCFD9DE) : (dark ? 0xFF444746 : 0xFFDADCE0);
    }

    static View create(Context context, String title, String message, Runnable onStop) {
        return new AlertOverlay(context).content(title, message, onStop);
    }

    static int width(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int available = context.getResources().getDisplayMetrics().widthPixels;
        return Math.min(
                Math.round(400 * density), Math.max(1, available - Math.round(48 * density)));
    }

    private View content(String title, String message, Runnable onStop) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable background = shape(surface, 28);
        background.setStroke(dp(1), outline);
        card.setBackground(background);
        card.setClipToOutline(true);
        card.setElevation(dp(6));

        TextView heading = text(context.getString(R.string.overlay_alert_title, title), 24, ink);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        // Heading and message scroll together so long content and large fonts keep Stop reachable.
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(heading);
        TextView body = text(message, 16, muted);
        body.setPadding(0, dp(16), 0, dp(8));
        copy.addView(body);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(copy);
        int maxCopyHeight =
                Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.45f);
        copy.measure(
                View.MeasureSpec.makeMeasureSpec(
                        Math.max(1, width(context) - dp(48)), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        card.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, Math.min(copy.getMeasuredHeight(), maxCopyHeight)));

        Button stop = new Button(context);
        stop.setText(R.string.confirm_stop_audio);
        stop.setAllCaps(false);
        stop.setTextSize(14);
        stop.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        stop.setTextColor(onPrimary);
        stop.setBackgroundTintList(null);
        stop.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((onPrimary & 0x00FFFFFF) | 0x33000000),
                        shape(primary, 24),
                        shape(0xFFFFFFFF, 24)));
        stop.setStateListAnimator(null);
        stop.setMinHeight(dp(48));
        stop.setMinimumHeight(dp(48));
        stop.setPadding(dp(24), dp(12), dp(24), dp(12));
        stop.setOnClickListener(v -> onStop.run());
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-1, -2);
        action.topMargin = dp(24);
        card.addView(stop, action);
        return card;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(4), 1);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
