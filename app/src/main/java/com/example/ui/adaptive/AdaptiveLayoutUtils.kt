package com.example.ui.adaptive

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass?> { null }
val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }
val LocalWindowHeightSizeClass = staticCompositionLocalOf { WindowHeightSizeClass.Medium }

object AdaptiveLayoutUtils {
    val windowSizeClass: WindowSizeClass?
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowSizeClass.current

    val windowWidthSizeClass: WindowWidthSizeClass
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowWidthSizeClass.current

    val windowHeightSizeClass: WindowHeightSizeClass
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowHeightSizeClass.current

    val isCompact: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Compact

    val isMedium: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Medium

    val isExpanded: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Expanded

    val isLandscape: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowHeightSizeClass.current == WindowHeightSizeClass.Compact
}
