package com.autodroid

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource

/** Central Material shape and type scales, backed by the native View resources. */
@Composable
internal fun appShapes() =
    Shapes(
        extraSmall = RoundedCornerShape(dimensionResource(R.dimen.corner_extra_small)),
        small = RoundedCornerShape(dimensionResource(R.dimen.corner_small)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.corner_medium)),
        large = RoundedCornerShape(dimensionResource(R.dimen.corner_card)),
        extraLarge = RoundedCornerShape(dimensionResource(R.dimen.corner_large)),
    )

@Composable
private fun textSize(resource: Int) =
    with(LocalDensity.current) { dimensionResource(resource).toSp() }

@Composable
internal fun appTypography(): Typography {
    val defaults = Typography()
    return defaults.copy(
        displayLarge =
            defaults.displayLarge.copy(
                fontSize = textSize(R.dimen.text_57),
                lineHeight = textSize(R.dimen.text_64),
            ),
        displayMedium =
            defaults.displayMedium.copy(
                fontSize = textSize(R.dimen.text_45),
                lineHeight = textSize(R.dimen.text_52),
            ),
        displaySmall =
            defaults.displaySmall.copy(
                fontSize = textSize(R.dimen.text_36),
                lineHeight = textSize(R.dimen.text_44),
            ),
        headlineLarge =
            defaults.headlineLarge.copy(
                fontSize = textSize(R.dimen.text_32),
                lineHeight = textSize(R.dimen.text_40),
            ),
        headlineMedium =
            defaults.headlineMedium.copy(
                fontSize = textSize(R.dimen.text_28),
                lineHeight = textSize(R.dimen.text_36),
            ),
        headlineSmall =
            defaults.headlineSmall.copy(
                fontSize = textSize(R.dimen.text_24),
                lineHeight = textSize(R.dimen.text_32),
            ),
        titleLarge =
            defaults.titleLarge.copy(
                fontSize = textSize(R.dimen.text_22),
                lineHeight = textSize(R.dimen.text_28),
            ),
        titleMedium =
            defaults.titleMedium.copy(
                fontSize = textSize(R.dimen.text_16),
                lineHeight = textSize(R.dimen.text_24),
            ),
        titleSmall =
            defaults.titleSmall.copy(
                fontSize = textSize(R.dimen.text_14),
                lineHeight = textSize(R.dimen.text_20),
            ),
        bodyLarge =
            defaults.bodyLarge.copy(
                fontSize = textSize(R.dimen.text_16),
                lineHeight = textSize(R.dimen.text_24),
            ),
        bodyMedium =
            defaults.bodyMedium.copy(
                fontSize = textSize(R.dimen.text_14),
                lineHeight = textSize(R.dimen.text_20),
            ),
        bodySmall =
            defaults.bodySmall.copy(
                fontSize = textSize(R.dimen.text_12),
                lineHeight = textSize(R.dimen.text_16),
            ),
        labelLarge =
            defaults.labelLarge.copy(
                fontSize = textSize(R.dimen.text_14),
                lineHeight = textSize(R.dimen.text_20),
            ),
        labelMedium =
            defaults.labelMedium.copy(
                fontSize = textSize(R.dimen.text_12),
                lineHeight = textSize(R.dimen.text_16),
            ),
        labelSmall =
            defaults.labelSmall.copy(
                fontSize = textSize(R.dimen.text_11),
                lineHeight = textSize(R.dimen.text_16),
            ),
    )
}
