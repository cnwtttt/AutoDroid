package com.autodroid

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource

/** Shared spacing scale; XML values also serve the native UI and service overlay. */
internal object AppDimensions {
    val size0
        @Composable get() = dimensionResource(R.dimen.space_0)

    val size1
        @Composable get() = dimensionResource(R.dimen.space_1)

    val size6
        @Composable get() = dimensionResource(R.dimen.space_6)

    val size8
        @Composable get() = dimensionResource(R.dimen.space_8)

    val size12
        @Composable get() = dimensionResource(R.dimen.space_12)

    val size16
        @Composable get() = dimensionResource(R.dimen.space_16)

    val size18
        @Composable get() = dimensionResource(R.dimen.space_18)

    val size20
        @Composable get() = dimensionResource(R.dimen.space_20)

    val size24
        @Composable get() = dimensionResource(R.dimen.space_24)

    val size28
        @Composable get() = dimensionResource(R.dimen.space_28)

    val size40
        @Composable get() = dimensionResource(R.dimen.space_40)

    val size100
        @Composable get() = dimensionResource(R.dimen.space_100)

    val size680
        @Composable get() = dimensionResource(R.dimen.space_680)
}
