package com.autodroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
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
        this.context = context;
        boolean dark =
                (context.getResources().getConfiguration().uiMode
                                & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        boolean x = !"play".equals(Store.prefs(context).getString("colorStyle", "x"));
        container = dark ? (x ? 0xFF16181C : 0xFF202124) : 0xFFF1F3F4;
        primary = x ? (dark ? 0xFFE7E9EA : 0xFF0F1419) : (dark ? 0xFFA8C7FA : 0xFF0B57D0);
        onPrimary = x ? (dark ? 0xFF0F1419 : 0xFFFFFFFF) : (dark ? 0xFF062E6F : 0xFFFFFFFF);
        ink = dark ? 0xFFE7E9EA : 0xFF0F1419;
        muted = dark ? 0xFFBDC1C6 : 0xFF536471;
        outline = dark ? 0xFF444746 : 0xFFDADCE0;
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    android.graphics.drawable.Drawable ripple(int color, int radius) {
        return new RippleDrawable(
                ColorStateList.valueOf(0x226080B0),
                shape(color, radius),
                shape(0xFFFFFFFF, radius));
    }

    LinearLayout column(int padding) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return l;
    }

    TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setFontFeatureSettings("kern");
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setLineSpacing(dp(3), 1);
        return v;
    }

    Button button(String text, boolean filled, Runnable action) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(filled ? onPrimary : primary);
        b.setBackground(ripple(filled ? primary : container, 24));
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setStateListAnimator(null);
        b.setOnClickListener(v -> action.run());
        return b;
    }
}
