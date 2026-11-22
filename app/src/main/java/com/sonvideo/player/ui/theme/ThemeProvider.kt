package com.sonvideo.player.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sonvideo.player.data.AppThemeMode

@Immutable
data class AppThemeState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val isDarkTheme: Boolean = true,
)

@Stable
class ThemeController(
    val state: AppThemeState,
    val setThemeMode: (AppThemeMode) -> Unit,
    val toggleTheme: () -> Unit,
    val setDynamicColor: (Boolean) -> Unit,
)

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    ThemeController(
        state = AppThemeState(),
        setThemeMode = {},
        toggleTheme = {},
        setDynamicColor = {},
    )
}

@Composable
fun AppThemeProvider(
    themeMode: AppThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val themeState = remember(themeMode, dynamicColor, isDark) {
        AppThemeState(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            isDarkTheme = isDark,
        )
    }

    val controller = remember(themeState, onThemeModeChange, onDynamicColorChange) {
        ThemeController(
            state = themeState,
            setThemeMode = onThemeModeChange,
            toggleTheme = {
                val next = if (themeState.isDarkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK
                onThemeModeChange(next)
            },
            setDynamicColor = onDynamicColorChange,
        )
    }

    CompositionLocalProvider(LocalThemeController provides controller) {
        SonPlayerTheme(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
