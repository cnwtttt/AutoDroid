package com.autodroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native controls used by permission and diagnostic dialogs alongside the Compose UI. */
final class DialogUi {
    final Context context;
    final int container, primary, onPrimary, ink, muted, outline;

    DialogUi(Context context) {
        this.context = UiTheme.palette(context);
        container = UiTheme.color(this.context, R.attr.uiSurfaceContainer);
        primary = UiTheme.color(this.context, R.attr.uiPrimary);
        onPrimary = UiTheme.color(this.context, R.attr.uiOnPrimary);
        ink = UiTheme.color(this.context, R.attr.uiOnSurface);
        muted = UiTheme.color(this.context, R.attr.uiOnSurfaceVariant);
        outline = UiTheme.color(this.context, R.attr.uiOutlineVariant);
    }

    int dimension(int resource) {
        return UiTheme.dimension(context, resource);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dimension(radius));
        return d;
    }

    android.graphics.drawable.Drawable ripple(int color, int radius) {
        return new RippleDrawable(
                ColorStateList.valueOf(UiTheme.color(this.context, R.attr.uiRipple)),
                shape(color, radius),
                shape(context.getColor(android.R.color.white), radius));
    }

    LinearLayout column(int padding) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(
                dimension(padding), dimension(padding), dimension(padding), dimension(padding));
        return l;
    }

    TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextAppearance(
                bold
                        ? R.style.TextAppearance_AutoDroid_Heading
                        : R.style.TextAppearance_AutoDroid_Body);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dimension(size));
        v.setTextColor(color);
        v.setFontFeatureSettings("kern");
        v.setLineSpacing(dimension(R.dimen.text_line_extra), 1);
        return v;
    }

    Button button(String text, boolean filled, Runnable action) {
        Button b = new Button(context, null, 0, R.style.Widget_AutoDroid_Button);
        b.setText(text);
        b.setTextColor(filled ? onPrimary : primary);
        b.setBackground(ripple(filled ? primary : container, R.dimen.corner_button));
        b.setPadding(
                dimension(R.dimen.space_20),
                dimension(R.dimen.space_10),
                dimension(R.dimen.space_20),
                dimension(R.dimen.space_10));
        b.setOnClickListener(v -> action.run());
        return b;
    }
}
