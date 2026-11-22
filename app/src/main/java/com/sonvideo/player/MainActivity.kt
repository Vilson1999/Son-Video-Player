package com.sonvideo.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.sonvideo.player.data.AppThemeMode
import com.sonvideo.player.data.PlaybackProgress
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.SettingsRepository
import com.sonvideo.player.data.VideoEntry
import com.sonvideo.player.data.VideoFolder
import com.sonvideo.player.media.MediaInfoHelper
import com.sonvideo.player.navigation.HomeRoute
import com.sonvideo.player.navigation.PlayerRoute
import com.sonvideo.player.navigation.SonPlayerNavHost
import com.sonvideo.player.ui.theme.AppThemeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SettingsRepository(application)

    val settings: StateFlow<PlayerSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerSettings())

    val libraryItems: StateFlow<List<VideoEntry>> = repository.libraryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<VideoFolder>> = repository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playbackPositions: StateFlow<Map<String, PlaybackProgress>> = repository.playbackPositionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val lastPlayedUri: StateFlow<String?> = repository.lastPlayedUriFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingExternalVideo = MutableStateFlow<Pair<String, String>?>(null)
    val pendingExternalVideo: StateFlow<Pair<String, String>?> = _pendingExternalVideo.asStateFlow()

    fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri = intent.data ?: return
            if (uri.scheme.equals("content", ignoreCase = true) ||
                uri.scheme.equals("file", ignoreCase = true)
            ) {
                val title = MediaInfoHelper.resolveDisplayName(getApplication(), uri)
                _pendingExternalVideo.value = uri.toString() to title
            }
        }
    }

    fun consumeExternalVideo() {
        _pendingExternalVideo.value = null
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(appTheme = mode) }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(dynamicColor = enabled) }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val playbackPositions by viewModel.playbackPositions.collectAsStateWithLifecycle()
            val lastPlayedUri by viewModel.lastPlayedUri.collectAsStateWithLifecycle()
            val pendingExternal by viewModel.pendingExternalVideo.collectAsStateWithLifecycle()

            AppThemeProvider(
                themeMode = settings.appTheme,
                dynamicColor = settings.dynamicColor,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor,
            ) {
                val navController = rememberNavController()

                LaunchedEffect(pendingExternal) {
                    val external = pendingExternal ?: return@LaunchedEffect
                    navController.navigate(PlayerRoute(uri = external.first, title = external.second))
                    viewModel.consumeExternalVideo()
                }

                SonPlayerNavHost(
                    navController = navController,
                    settings = settings,
                    libraryItems = libraryItems,
                    folders = folders,
                    playbackPositions = playbackPositions,
                    lastPlayedUri = lastPlayedUri,
                    repository = viewModel.repository,
                    startDestination = HomeRoute,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }
}
