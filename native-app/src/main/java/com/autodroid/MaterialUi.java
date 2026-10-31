package com.autodroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared Material 3 inspired tokens for the dependency-free native UI. */
final class MaterialUi {
    final Context context;
    final int background,
            surface,
            container,
            primary,
            onPrimary,
            ink,
            muted,
            outline,
            accent,
            onAccent;

    MaterialUi(Context context) {
        this.context = new android.view.ContextThemeWrapper(context, R.style.Palette_Native);
        background = UiTheme.color(this.context, R.attr.uiBackground);
        surface = UiTheme.color(this.context, R.attr.uiSurface);
        container = UiTheme.color(this.context, R.attr.uiSurfaceContainer);
        primary = UiTheme.color(this.context, R.attr.uiPrimary);
        onPrimary = UiTheme.color(this.context, R.attr.uiOnPrimary);
        ink = UiTheme.color(this.context, R.attr.uiOnSurface);
        muted = UiTheme.color(this.context, R.attr.uiOnSurfaceVariant);
        outline = UiTheme.color(this.context, R.attr.uiOutlineVariant);
        accent = UiTheme.color(this.context, R.attr.uiPrimaryContainer);
        onAccent = UiTheme.color(this.context, R.attr.uiOnPrimaryContainer);
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

    LinearLayout row() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
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

    void space(LinearLayout parent, int height) {
        parent.addView(new View(context), new LinearLayout.LayoutParams(1, dimension(height)));
    }

    LinearLayout card(LinearLayout parent, int color) {
        LinearLayout card = column(R.dimen.space_20);
        card.setBackground(shape(color, R.dimen.corner_button));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dimension(R.dimen.space_16);
        parent.addView(card, lp);
        return card;
    }

    ImageView icon(String kind, int color) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(new Symbol(kind, color));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private static final class Symbol extends Drawable {
        final String kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Symbol(String kind, int color) {
            this.kind = kind;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        public void draw(Canvas canvas) {
            canvas.save();
            Rect b = getBounds();
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / 24f, b.height() / 24f);
            if (kind.equals("add")) {
                canvas.drawLine(5, 12, 19, 12, p);
                canvas.drawLine(12, 5, 12, 19, p);
            } else if (kind.equals("macros")) {
                canvas.drawRoundRect(3, 3, 10, 10, 2, 2, p);
                canvas.drawRoundRect(14, 14, 21, 21, 2, 2, p);
                canvas.drawLine(14, 6, 18, 6, p);
                canvas.drawLine(18, 6, 18, 10, p);
                canvas.drawLine(6, 14, 6, 18, p);
                canvas.drawLine(6, 18, 10, 18, p);
            } else if (kind.equals("logs")) {
                canvas.drawRoundRect(5, 3, 19, 21, 2, 2, p);
                canvas.drawLine(9, 8, 15, 8, p);
                canvas.drawLine(9, 12, 15, 12, p);
                canvas.drawLine(9, 16, 13, 16, p);
            } else if (kind.equals("settings")) {
                canvas.drawCircle(12, 12, 7, p);
                canvas.drawCircle(12, 12, 2.5f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    canvas.drawLine(
                            12 + 7 * (float) Math.cos(a),
                            12 + 7 * (float) Math.sin(a),
                            12 + 9 * (float) Math.cos(a),
                            12 + 9 * (float) Math.sin(a),
                            p);
                }
            } else {
                canvas.drawRoundRect(3, 4, 21, 18, 3, 3, p);
                canvas.drawLine(7, 18, 7, 21, p);
                canvas.drawLine(7, 21, 11, 18, p);
                canvas.drawLine(7, 9, 17, 9, p);
                canvas.drawLine(7, 13, 14, 13, p);
            }
            canvas.restore();
        }

        public void setAlpha(int a) {
            p.setAlpha(a);
        }

        public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }

        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
