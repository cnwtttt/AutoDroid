package com.autodroid

import android.content.Context

/** Stable preference values; old numeric preview choices do not override the X default. */
internal enum class ColorStyle(val preferenceValue: String) {
    X("x"),
    PLAY("play");

    companion object {
        fun read(context: Context): ColorStyle {
            val value = Store.prefs(context).getString("colorStyle", X.preferenceValue)
            return entries.firstOrNull { it.preferenceValue == value } ?: X
        }
    }
}
