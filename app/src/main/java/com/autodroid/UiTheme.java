package com.autodroid;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

/** Shared theme access for Compose and framework Views, including service windows. */
final class UiTheme {
    private UiTheme() {}

    static Context palette(Context context, String style) {
        return new ContextThemeWrapper(
                context, "play".equals(style) ? R.style.Palette_Play : R.style.Palette_X);
    }

    static Context palette(Context context) {
        return palette(context, Store.prefs(context).getString("colorStyle", "x"));
    }

    static int color(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            throw new IllegalArgumentException("Missing theme color: " + attribute);
        }
        return value.resourceId == 0 ? value.data : context.getColor(value.resourceId);
    }

    static int dimension(Context context, int resource) {
        return context.getResources().getDimensionPixelSize(resource);
    }
}
