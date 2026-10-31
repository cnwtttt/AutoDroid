package com.autodroid;

import android.content.Context;
import android.content.res.ColorStateList;
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
        this.context = UiTheme.palette(context);
        surface = UiTheme.color(this.context, R.attr.uiSurfaceContainerHigh);
        primary = UiTheme.color(this.context, R.attr.uiPrimary);
        onPrimary = UiTheme.color(this.context, R.attr.uiOnPrimary);
        ink = UiTheme.color(this.context, R.attr.uiOnSurface);
        muted = UiTheme.color(this.context, R.attr.uiOnSurfaceVariant);
        outline = UiTheme.color(this.context, R.attr.uiOutlineVariant);
    }

    static View create(Context context, String title, String message, Runnable onStop) {
        return new AlertOverlay(context).content(title, message, onStop);
    }

    static int width(Context context) {
        int available = context.getResources().getDisplayMetrics().widthPixels;
        return Math.min(
                UiTheme.dimension(context, R.dimen.space_400),
                Math.max(1, available - UiTheme.dimension(context, R.dimen.space_48)));
    }

    private View content(String title, String message, Runnable onStop) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dimension(R.dimen.space_24),
                dimension(R.dimen.space_24),
                dimension(R.dimen.space_24),
                dimension(R.dimen.space_24));
        GradientDrawable background = shape(surface, R.dimen.corner_large);
        background.setStroke(dimension(R.dimen.space_1), outline);
        card.setBackground(background);
        card.setClipToOutline(true);
        card.setElevation(dimension(R.dimen.space_6));

        TextView heading =
                text(context.getString(R.string.overlay_alert_title, title), R.dimen.text_24, ink);
        heading.setTextAppearance(R.style.TextAppearance_AutoDroid_Heading);
        heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dimension(R.dimen.text_24));
        heading.setTextColor(ink);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        // Heading and message scroll together so long content and large fonts keep Stop reachable.
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(heading);
        TextView body = text(message, R.dimen.text_16, muted);
        body.setPadding(0, dimension(R.dimen.space_16), 0, dimension(R.dimen.space_8));
        copy.addView(body);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(copy);
        int maxCopyHeight =
                Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.45f);
        copy.measure(
                View.MeasureSpec.makeMeasureSpec(
                        Math.max(1, width(context) - dimension(R.dimen.space_48)),
                        View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        card.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, Math.min(copy.getMeasuredHeight(), maxCopyHeight)));

        Button stop = new Button(context, null, 0, R.style.Widget_AutoDroid_Button);
        stop.setText(R.string.confirm_stop_audio);
        stop.setTextColor(onPrimary);
        stop.setBackgroundTintList(null);
        stop.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(UiTheme.color(this.context, R.attr.uiRipple)),
                        shape(primary, R.dimen.corner_button),
                        shape(context.getColor(android.R.color.white), R.dimen.corner_button)));
        stop.setPadding(
                dimension(R.dimen.space_24),
                dimension(R.dimen.space_12),
                dimension(R.dimen.space_24),
                dimension(R.dimen.space_12));
        stop.setOnClickListener(v -> onStop.run());
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-1, -2);
        action.topMargin = dimension(R.dimen.space_24);
        card.addView(stop, action);
        return card;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextAppearance(R.style.TextAppearance_AutoDroid_Body);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dimension(size));
        view.setTextColor(color);
        view.setLineSpacing(dimension(R.dimen.space_4), 1);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dimension(radius));
        return drawable;
    }

    private int dimension(int resource) {
        return UiTheme.dimension(context, resource);
    }
}
