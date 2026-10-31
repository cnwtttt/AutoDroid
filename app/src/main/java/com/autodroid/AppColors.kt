package com.autodroid

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** One palette source for Compose, framework dialogs and service overlays. */
internal fun appColorScheme(context: Context, style: ColorStyle): ColorScheme {
    val themed = UiTheme.palette(context, style.preferenceValue)
    return lightColorScheme(
        primary = Color(UiTheme.color(themed, R.attr.uiPrimary)),
        onPrimary = Color(UiTheme.color(themed, R.attr.uiOnPrimary)),
        primaryContainer = Color(UiTheme.color(themed, R.attr.uiPrimaryContainer)),
        onPrimaryContainer = Color(UiTheme.color(themed, R.attr.uiOnPrimaryContainer)),
        secondary = Color(UiTheme.color(themed, R.attr.uiSecondary)),
        onSecondary = Color(UiTheme.color(themed, R.attr.uiOnSecondary)),
        secondaryContainer = Color(UiTheme.color(themed, R.attr.uiSecondaryContainer)),
        onSecondaryContainer = Color(UiTheme.color(themed, R.attr.uiOnSecondaryContainer)),
        tertiary = Color(UiTheme.color(themed, R.attr.uiTertiary)),
        onTertiary = Color(UiTheme.color(themed, R.attr.uiOnTertiary)),
        tertiaryContainer = Color(UiTheme.color(themed, R.attr.uiTertiaryContainer)),
        onTertiaryContainer = Color(UiTheme.color(themed, R.attr.uiOnTertiaryContainer)),
        background = Color(UiTheme.color(themed, R.attr.uiBackground)),
        onBackground = Color(UiTheme.color(themed, R.attr.uiOnBackground)),
        surface = Color(UiTheme.color(themed, R.attr.uiSurface)),
        onSurface = Color(UiTheme.color(themed, R.attr.uiOnSurface)),
        surfaceVariant = Color(UiTheme.color(themed, R.attr.uiSurfaceVariant)),
        onSurfaceVariant = Color(UiTheme.color(themed, R.attr.uiOnSurfaceVariant)),
        surfaceDim = Color(UiTheme.color(themed, R.attr.uiSurfaceDim)),
        surfaceBright = Color(UiTheme.color(themed, R.attr.uiSurfaceBright)),
        surfaceContainerLowest = Color(UiTheme.color(themed, R.attr.uiSurfaceContainerLowest)),
        surfaceContainerLow = Color(UiTheme.color(themed, R.attr.uiSurfaceContainerLow)),
        surfaceContainer = Color(UiTheme.color(themed, R.attr.uiSurfaceContainer)),
        surfaceContainerHigh = Color(UiTheme.color(themed, R.attr.uiSurfaceContainerHigh)),
        surfaceContainerHighest = Color(UiTheme.color(themed, R.attr.uiSurfaceContainerHighest)),
        surfaceTint = Color(UiTheme.color(themed, R.attr.uiSurfaceTint)),
        outline = Color(UiTheme.color(themed, R.attr.uiOutline)),
        outlineVariant = Color(UiTheme.color(themed, R.attr.uiOutlineVariant)),
        inverseSurface = Color(UiTheme.color(themed, R.attr.uiInverseSurface)),
        inverseOnSurface = Color(UiTheme.color(themed, R.attr.uiInverseOnSurface)),
        inversePrimary = Color(UiTheme.color(themed, R.attr.uiInversePrimary)),
        error = Color(UiTheme.color(themed, R.attr.uiError)),
        onError = Color(UiTheme.color(themed, R.attr.uiOnError)),
        errorContainer = Color(UiTheme.color(themed, R.attr.uiErrorContainer)),
        onErrorContainer = Color(UiTheme.color(themed, R.attr.uiOnErrorContainer)),
    )
}
