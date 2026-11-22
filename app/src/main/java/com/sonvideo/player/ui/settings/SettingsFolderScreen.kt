package com.sonvideo.player.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonvideo.player.R
import com.sonvideo.player.data.AppThemeMode
import com.sonvideo.player.data.BufferProfile
import com.sonvideo.player.data.DecoderMode
import com.sonvideo.player.data.LibrarySortOption
import com.sonvideo.player.data.OrientationMode
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.RepeatOption
import com.sonvideo.player.data.SettingsRepository
import com.sonvideo.player.data.SubtitleScale
import com.sonvideo.player.data.VideoFolder
import com.sonvideo.player.data.VideoResizeMode
import com.sonvideo.player.media.MediaInfoHelper
import com.sonvideo.player.ui.theme.LocalThemeController
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFolderScreen(
    category: SettingsFolderCategory,
    settings: PlayerSettings,
    folders: List<VideoFolder>,
    repository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val themeController = LocalThemeController.current
    val themeState = themeController.state

    var activePicker by remember { mutableStateOf<SettingsPickerState?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val folderName = MediaInfoHelper.resolveFolderName(context, treeUri)
            val folder = VideoFolder(
                treeUri = treeUri.toString(),
                name = folderName,
                addedAtMs = System.currentTimeMillis(),
            )
            scope.launch {
                repository.addOrUpdateFolder(folder)
                snackbarHostState.showSnackbar("Added folder: $folderName")
            }
        }
    }

    fun update(transform: (PlayerSettings) -> PlayerSettings) {
        scope.launch { repository.updateSettings(transform) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = category.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Settings Folder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_folder_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("settings_folder_detail_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (category) {
                    SettingsFolderCategory.PLAYER -> {
                        item {
                            SettingsSection(title = "Playback Behavior") {
                                SettingSwitchRow(
                                    icon = Icons.Rounded.PlayCircleOutline,
                                    title = "Auto-Play on Open",
                                    subtitle = "Start playing immediately when opening a video",
                                    checked = settings.autoPlay,
                                    onCheckedChange = { v -> update { it.copy(autoPlay = v) } },
                                    testTag = "setting_auto_play",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.PlayCircleOutline,
                                    title = "Resume Playback Position",
                                    subtitle = "Save and restore playback progress per video",
                                    checked = settings.resumePlayback,
                                    onCheckedChange = { v -> update { it.copy(resumePlayback = v) } },
                                    testTag = "setting_resume_playback",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.Speed,
                                    title = "Apply Default Speed",
                                    subtitle = "Always start videos at your default speed below",
                                    checked = settings.rememberSpeed,
                                    onCheckedChange = { v -> update { it.copy(rememberSpeed = v) } },
                                    testTag = "setting_remember_speed",
                                )
                                SettingDivider()
                                SettingSliderRow(
                                    title = "Default Playback Speed",
                                    valueLabel = String.format(Locale.US, "%.2fx", settings.defaultSpeed),
                                    value = settings.defaultSpeed,
                                    valueRange = 0.5f..2.0f,
                                    steps = 5,
                                    onValueChange = { raw ->
                                        val stepped = (raw * 4f).roundToInt() / 4f
                                        update { it.copy(defaultSpeed = stepped) }
                                    },
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.RestartAlt,
                                    title = "Repeat / Loop Mode",
                                    subtitle = settings.repeatMode.label,
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Repeat Mode",
                                            options = RepeatOption.entries.map { it.label to "" },
                                            selectedIndex = RepeatOption.entries.indexOf(settings.repeatMode),
                                            onSelect = { idx -> update { it.copy(repeatMode = RepeatOption.entries[idx]) } },
                                        )
                                    },
                                    testTag = "setting_repeat_mode",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.FastForward,
                                    title = "Fast Keyframe Seeking",
                                    subtitle = "Snap seeks to nearest sync frame for instant scrubbing",
                                    checked = settings.fastSeek,
                                    onCheckedChange = { v -> update { it.copy(fastSeek = v) } },
                                    testTag = "setting_fast_seek",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.AudioFile,
                                    title = "Background Audio",
                                    subtitle = "Keep audio playing when switching away from the app",
                                    checked = settings.backgroundPlay,
                                    onCheckedChange = { v -> update { it.copy(backgroundPlay = v) } },
                                    testTag = "setting_background_play",
                                )
                            }
                        }
                        item {
                            SettingsSection(title = "Video & Display") {
                                SettingChoiceRow(
                                    icon = Icons.Rounded.DisplaySettings,
                                    title = "Default Resize Mode",
                                    subtitle = settings.defaultResizeMode.label,
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Default Resize Mode",
                                            options = VideoResizeMode.entries.map { it.label to "" },
                                            selectedIndex = VideoResizeMode.entries.indexOf(settings.defaultResizeMode),
                                            onSelect = { idx -> update { it.copy(defaultResizeMode = VideoResizeMode.entries[idx]) } },
                                        )
                                    },
                                    testTag = "setting_resize_mode",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.ScreenRotation,
                                    title = "Player Orientation",
                                    subtitle = settings.defaultOrientation.label,
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Player Orientation",
                                            options = OrientationMode.entries.map { it.label to "" },
                                            selectedIndex = OrientationMode.entries.indexOf(settings.defaultOrientation),
                                            onSelect = { idx -> update { it.copy(defaultOrientation = OrientationMode.entries[idx]) } },
                                        )
                                    },
                                    testTag = "setting_orientation",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.Brightness6,
                                    title = "Keep Screen On",
                                    subtitle = "Prevent display sleep during active video playback",
                                    checked = settings.keepScreenOn,
                                    onCheckedChange = { v -> update { it.copy(keepScreenOn = v) } },
                                    testTag = "setting_keep_screen_on",
                                )
                            }
                        }
                    }

                    SettingsFolderCategory.CODEC -> {
                        item {
                            SettingsSection(title = "NextLib Decoder & Engine") {
                                SettingChoiceRow(
                                    icon = Icons.Rounded.Memory,
                                    title = "Decoder Engine Priority",
                                    subtitle = "${settings.decoderMode.label} — ${settings.decoderMode.description}",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Decoder Engine Priority",
                                            options = DecoderMode.entries.map { it.label to it.description },
                                            selectedIndex = DecoderMode.entries.indexOf(settings.decoderMode),
                                            onSelect = { idx -> update { it.copy(decoderMode = DecoderMode.entries[idx]) } },
                                        )
                                    },
                                    testTag = "setting_decoder_mode",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.Tune,
                                    title = "Decoder Fallback",
                                    subtitle = "Automatically fall back to NextLib software codec if hardware fails",
                                    checked = settings.enableDecoderFallback,
                                    onCheckedChange = { v -> update { it.copy(enableDecoderFallback = v) } },
                                    testTag = "setting_decoder_fallback",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.Speed,
                                    title = "Buffer Profile",
                                    subtitle = "${settings.bufferProfile.label} — ${settings.bufferProfile.description}",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Buffer Profile",
                                            options = BufferProfile.entries.map { it.label to it.description },
                                            selectedIndex = BufferProfile.entries.indexOf(settings.bufferProfile),
                                            onSelect = { idx -> update { it.copy(bufferProfile = BufferProfile.entries[idx]) } },
                                        )
                                    },
                                    testTag = "setting_buffer_profile",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.DisplaySettings,
                                    title = "Show Codec & Resolution Badge",
                                    subtitle = "Display live video resolution, codec, and decoder info in HUD",
                                    checked = settings.showCodecBadge,
                                    onCheckedChange = { v -> update { it.copy(showCodecBadge = v) } },
                                    testTag = "setting_codec_badge",
                                )
                            }
                        }
                    }

                    SettingsFolderCategory.AUDIO_SUBTITLES -> {
                        item {
                            val audioLangs = listOf(
                                "" to "System Default",
                                "en" to "English",
                                "hi" to "Hindi",
                                "ja" to "Japanese",
                                "es" to "Spanish",
                                "fr" to "French",
                            )
                            val subLangs = listOf(
                                "" to "Auto / Default",
                                "off" to "Disabled",
                                "en" to "English",
                                "hi" to "Hindi",
                                "ja" to "Japanese",
                                "es" to "Spanish",
                            )
                            SettingsSection(title = "Audio & Subtitles") {
                                SettingSwitchRow(
                                    icon = Icons.Rounded.AudioFile,
                                    title = "Audio Offload",
                                    subtitle = "Offload compressed audio decoding to hardware DSP when supported",
                                    checked = settings.enableAudioOffload,
                                    onCheckedChange = { v -> update { it.copy(enableAudioOffload = v) } },
                                    testTag = "setting_audio_offload",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.AudioFile,
                                    title = "Preferred Audio Language",
                                    subtitle = audioLangs.firstOrNull { it.first == settings.preferredAudioLang }?.second
                                        ?: "System Default",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Preferred Audio Language",
                                            options = audioLangs.map { it.second to "" },
                                            selectedIndex = audioLangs.indexOfFirst { it.first == settings.preferredAudioLang }
                                                .coerceAtLeast(0),
                                            onSelect = { idx -> update { it.copy(preferredAudioLang = audioLangs[idx].first) } },
                                        )
                                    },
                                    testTag = "setting_audio_lang",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.ClosedCaption,
                                    title = "Preferred Subtitle Language",
                                    subtitle = subLangs.firstOrNull { it.first == settings.preferredSubtitleLang }?.second
                                        ?: "Auto / Default",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Preferred Subtitle Language",
                                            options = subLangs.map { it.second to "" },
                                            selectedIndex = subLangs.indexOfFirst { it.first == settings.preferredSubtitleLang }
                                                .coerceAtLeast(0),
                                            onSelect = { idx -> update { it.copy(preferredSubtitleLang = subLangs[idx].first) } },
                                        )
                                    },
                                    testTag = "setting_subtitle_lang",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.Subtitles,
                                    title = "Subtitle Text Size",
                                    subtitle = settings.subtitleScale.label,
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Subtitle Text Size",
                                            options = SubtitleScale.entries.map { it.label to "" },
                                            selectedIndex = SubtitleScale.entries.indexOf(settings.subtitleScale),
                                            onSelect = { idx -> update { it.copy(subtitleScale = SubtitleScale.entries[idx]) } },
                                        )
                                    },
                                    testTag = "setting_subtitle_scale",
                                )
                            }
                        }
                    }

                    SettingsFolderCategory.GESTURES -> {
                        item {
                            val seekOptions = listOf(5, 10, 15, 30)
                            val timeoutOptions = listOf(2, 4, 6, 8)
                            SettingsSection(title = "Controls & Touch Gestures") {
                                SettingChoiceRow(
                                    icon = Icons.Rounded.FastForward,
                                    title = "Seek Increment",
                                    subtitle = "${settings.seekIncrementSec} seconds per double-tap / button",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Seek Increment",
                                            options = seekOptions.map { "$it seconds" to "" },
                                            selectedIndex = seekOptions.indexOf(settings.seekIncrementSec).coerceAtLeast(0),
                                            onSelect = { idx -> update { it.copy(seekIncrementSec = seekOptions[idx]) } },
                                        )
                                    },
                                    testTag = "setting_seek_increment",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.TouchApp,
                                    title = "Controls Auto-Hide Timeout",
                                    subtitle = "${settings.controlsTimeoutSec} seconds",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Controls Auto-Hide Timeout",
                                            options = timeoutOptions.map { "$it seconds" to "" },
                                            selectedIndex = timeoutOptions.indexOf(settings.controlsTimeoutSec).coerceAtLeast(0),
                                            onSelect = { idx -> update { it.copy(controlsTimeoutSec = timeoutOptions[idx]) } },
                                        )
                                    },
                                    testTag = "setting_controls_timeout",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.TouchApp,
                                    title = "Double-Tap to Seek",
                                    subtitle = "Double tap left or right side of player to jump",
                                    checked = settings.doubleTapSeek,
                                    onCheckedChange = { v -> update { it.copy(doubleTapSeek = v) } },
                                    testTag = "setting_double_tap_seek",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.Brightness6,
                                    title = "Swipe Brightness (Left)",
                                    subtitle = "Drag vertically on left half to adjust screen brightness",
                                    checked = settings.brightnessGesture,
                                    onCheckedChange = { v -> update { it.copy(brightnessGesture = v) } },
                                    testTag = "setting_brightness_gesture",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.AudioFile,
                                    title = "Swipe Volume (Right)",
                                    subtitle = "Drag vertically on right half to adjust media volume",
                                    checked = settings.volumeGesture,
                                    onCheckedChange = { v -> update { it.copy(volumeGesture = v) } },
                                    testTag = "setting_volume_gesture",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.FastForward,
                                    title = "Horizontal Swipe Seek",
                                    subtitle = "Drag left or right across video to scrub timeline",
                                    checked = settings.seekGesture,
                                    onCheckedChange = { v -> update { it.copy(seekGesture = v) } },
                                    testTag = "setting_seek_gesture",
                                )
                            }
                        }
                    }

                    SettingsFolderCategory.APPEARANCE -> {
                        item {
                            SettingsSection(title = "Theme & Colors") {
                                ThemeModeToggleRow(
                                    selectedMode = themeState.themeMode,
                                    isDarkActive = themeState.isDarkTheme,
                                    onSelectMode = { mode -> themeController.setThemeMode(mode) },
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.Rounded.DarkMode,
                                    title = "Quick Dark Mode Toggle",
                                    subtitle = if (themeState.isDarkTheme) "Obsidian Dark active" else "Daylight Light active",
                                    checked = themeState.isDarkTheme,
                                    onCheckedChange = { wantDark ->
                                        themeController.setThemeMode(
                                            if (wantDark) AppThemeMode.DARK else AppThemeMode.LIGHT
                                        )
                                    },
                                    testTag = "setting_dark_mode_toggle",
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    SettingDivider()
                                    SettingSwitchRow(
                                        icon = Icons.Rounded.Palette,
                                        title = "Material You Dynamic Color",
                                        subtitle = "Adapt colors to your system wallpaper",
                                        checked = themeState.dynamicColor,
                                        onCheckedChange = { enabled -> themeController.setDynamicColor(enabled) },
                                        testTag = "setting_dynamic_color",
                                    )
                                }
                            }
                        }
                    }

                    SettingsFolderCategory.FOLDERS -> {
                        item {
                            SettingsSection(title = "Video Folders") {
                                SettingChoiceRow(
                                    icon = Icons.Rounded.CreateNewFolder,
                                    title = "Add Video Folder",
                                    subtitle = "Link a device folder to scan offline videos automatically",
                                    onClick = { folderPickerLauncher.launch(null) },
                                    testTag = "setting_add_video_folder",
                                )
                                folders.forEach { folder ->
                                    SettingDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "Linked SAF folder",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.removeFolder(folder.treeUri)
                                                    snackbarHostState.showSnackbar("Removed ${folder.name}")
                                                }
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Remove folder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSection(title = "Sorting & Data") {
                                SettingChoiceRow(
                                    icon = Icons.AutoMirrored.Rounded.Sort,
                                    title = "Default Sort Order",
                                    subtitle = "${settings.librarySort.label} (${if (settings.librarySortAscending) "Ascending" else "Descending"})",
                                    onClick = {
                                        activePicker = SettingsPickerState(
                                            title = "Default Sort By",
                                            options = LibrarySortOption.entries.map { it.label to "" },
                                            selectedIndex = LibrarySortOption.entries.indexOf(settings.librarySort),
                                            onSelect = { idx ->
                                                val chosen = LibrarySortOption.entries[idx]
                                                update {
                                                    it.copy(
                                                        librarySort = chosen,
                                                        librarySortAscending = chosen == LibrarySortOption.NAME,
                                                    )
                                                }
                                            },
                                        )
                                    },
                                    testTag = "setting_library_sort",
                                )
                                SettingDivider()
                                SettingSwitchRow(
                                    icon = Icons.AutoMirrored.Rounded.Sort,
                                    title = "Ascending Sort Order",
                                    subtitle = if (settings.librarySortAscending) "A–Z / Oldest / Smallest first" else "Z–A / Newest / Largest first",
                                    checked = settings.librarySortAscending,
                                    onCheckedChange = { asc -> update { it.copy(librarySortAscending = asc) } },
                                    testTag = "setting_library_sort_asc",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.DeleteSweep,
                                    title = "Clear Library & History",
                                    subtitle = "Remove saved resume positions, added files, and linked folders",
                                    onClick = {
                                        scope.launch {
                                            repository.clearHistory()
                                            snackbarHostState.showSnackbar("Library and playback history cleared")
                                        }
                                    },
                                    testTag = "setting_clear_history",
                                )
                                SettingDivider()
                                SettingChoiceRow(
                                    icon = Icons.Rounded.RestartAlt,
                                    title = "Reset All Settings",
                                    subtitle = "Restore recommended NextLib & player defaults",
                                    onClick = {
                                        scope.launch {
                                            repository.resetSettingsToDefaults()
                                            snackbarHostState.showSnackbar("Settings restored to defaults")
                                        }
                                    },
                                    testTag = "setting_reset_defaults",
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    activePicker?.let { picker ->
        SettingsPickerDialog(
            picker = picker,
            onDismiss = { activePicker = null },
        )
    }
}
