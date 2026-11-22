package com.sonvideo.player.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sonvideo.player.data.PlaybackProgress
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.SettingsRepository
import com.sonvideo.player.data.VideoEntry
import com.sonvideo.player.data.VideoFolder
import com.sonvideo.player.ui.home.FolderVideosScreen
import com.sonvideo.player.ui.home.HomeScreen
import com.sonvideo.player.ui.player.PlayerScreen
import com.sonvideo.player.ui.settings.SettingsFolderCategory
import com.sonvideo.player.ui.settings.SettingsFolderScreen
import com.sonvideo.player.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class FolderRoute(val folderName: String, val treeUri: String = "")

@Serializable
data class PlayerRoute(val uri: String, val title: String)

@Serializable
data object SettingsRoute

@Serializable
data class SettingsFolderRoute(val category: SettingsFolderCategory)

@Composable
fun SonPlayerNavHost(
    navController: NavHostController,
    settings: PlayerSettings,
    libraryItems: List<VideoEntry>,
    folders: List<VideoFolder>,
    playbackPositions: Map<String, PlaybackProgress>,
    lastPlayedUri: String?,
    repository: SettingsRepository,
    startDestination: Any = HomeRoute,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                settings = settings,
                libraryItems = libraryItems,
                folders = folders,
                repository = repository,
                onOpenFolder = { folderName, treeUri ->
                    navController.navigate(
                        FolderRoute(folderName = folderName, treeUri = treeUri.orEmpty())
                    )
                },
                onPlayVideo = { uri, title ->
                    navController.navigate(PlayerRoute(uri = uri, title = title))
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<FolderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderRoute>()
            FolderVideosScreen(
                folderName = route.folderName,
                treeUri = route.treeUri,
                settings = settings,
                libraryItems = libraryItems,
                playbackPositions = playbackPositions,
                repository = repository,
                onBack = { navController.popBackStack() },
                onPlayVideo = { uri, title ->
                    navController.navigate(PlayerRoute(uri = uri, title = title))
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayerRoute>()
            PlayerScreen(
                uri = route.uri,
                title = route.title,
                settings = settings,
                savedProgress = playbackPositions[route.uri],
                repository = repository,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(HomeRoute) {
                            popUpTo<PlayerRoute> { inclusive = true }
                        }
                    }
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                settings = settings,
                folders = folders,
                onOpenFolder = { category ->
                    navController.navigate(SettingsFolderRoute(category = category))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsFolderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsFolderRoute>()
            SettingsFolderScreen(
                category = route.category,
                settings = settings,
                folders = folders,
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
