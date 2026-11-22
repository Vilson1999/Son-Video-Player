package com.sonvideo.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "son_player_prefs")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val DECODER_FALLBACK = booleanPreferencesKey("decoder_fallback")
        val AUDIO_OFFLOAD = booleanPreferencesKey("audio_offload")
        val BUFFER_PROFILE = stringPreferencesKey("buffer_profile")

        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val REMEMBER_SPEED = booleanPreferencesKey("remember_speed")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val FAST_SEEK = booleanPreferencesKey("fast_seek")
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")

        val DEFAULT_RESIZE_MODE = stringPreferencesKey("default_resize_mode")
        val DEFAULT_ORIENTATION = stringPreferencesKey("default_orientation")
        val SHOW_CODEC_BADGE = booleanPreferencesKey("show_codec_badge")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        val SEEK_INCREMENT_SEC = intPreferencesKey("seek_increment_sec")
        val CONTROLS_TIMEOUT_SEC = intPreferencesKey("controls_timeout_sec")
        val DOUBLE_TAP_SEEK = booleanPreferencesKey("double_tap_seek")
        val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
        val VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
        val SEEK_GESTURE = booleanPreferencesKey("seek_gesture")

        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val SUBTITLE_SCALE = stringPreferencesKey("subtitle_scale")

        val APP_THEME = stringPreferencesKey("app_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_SORT_ASC = booleanPreferencesKey("library_sort_asc")

        val LIBRARY_ITEMS_JSON = stringPreferencesKey("library_items_json")
        val VIDEO_FOLDERS_JSON = stringPreferencesKey("video_folders_json")
        val PLAYBACK_POSITIONS_JSON = stringPreferencesKey("playback_positions_json")
        val LAST_PLAYED_URI = stringPreferencesKey("last_played_uri")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        PlayerSettings(
            decoderMode = prefs[Keys.DECODER_MODE].enumOrDefault(DecoderMode.AUTO),
            enableDecoderFallback = prefs[Keys.DECODER_FALLBACK] ?: true,
            enableAudioOffload = prefs[Keys.AUDIO_OFFLOAD] ?: false,
            bufferProfile = prefs[Keys.BUFFER_PROFILE].enumOrDefault(BufferProfile.LITE),

            autoPlay = prefs[Keys.AUTO_PLAY] ?: true,
            resumePlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
            rememberSpeed = prefs[Keys.REMEMBER_SPEED] ?: false,
            defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            repeatMode = prefs[Keys.REPEAT_MODE].enumOrDefault(RepeatOption.OFF),
            fastSeek = prefs[Keys.FAST_SEEK] ?: true,
            backgroundPlay = prefs[Keys.BACKGROUND_PLAY] ?: false,

            defaultResizeMode = prefs[Keys.DEFAULT_RESIZE_MODE].enumOrDefault(VideoResizeMode.FIT),
            defaultOrientation = prefs[Keys.DEFAULT_ORIENTATION].enumOrDefault(OrientationMode.AUTO),
            showCodecBadge = prefs[Keys.SHOW_CODEC_BADGE] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,

            seekIncrementSec = prefs[Keys.SEEK_INCREMENT_SEC] ?: 10,
            controlsTimeoutSec = prefs[Keys.CONTROLS_TIMEOUT_SEC] ?: 4,
            doubleTapSeek = prefs[Keys.DOUBLE_TAP_SEEK] ?: true,
            brightnessGesture = prefs[Keys.BRIGHTNESS_GESTURE] ?: true,
            volumeGesture = prefs[Keys.VOLUME_GESTURE] ?: true,
            seekGesture = prefs[Keys.SEEK_GESTURE] ?: true,

            preferredAudioLang = prefs[Keys.PREFERRED_AUDIO_LANG] ?: "",
            preferredSubtitleLang = prefs[Keys.PREFERRED_SUBTITLE_LANG] ?: "",
            subtitleScale = prefs[Keys.SUBTITLE_SCALE].enumOrDefault(SubtitleScale.MEDIUM),

            appTheme = prefs[Keys.APP_THEME].enumOrDefault(AppThemeMode.DARK),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            librarySort = prefs[Keys.LIBRARY_SORT].enumOrDefault(LibrarySortOption.NAME),
            librarySortAscending = prefs[Keys.LIBRARY_SORT_ASC] ?: true,
        )
    }

    val libraryFlow: Flow<List<VideoEntry>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LIBRARY_ITEMS_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<VideoEntry>>(raw) }.getOrDefault(emptyList())
    }

    val foldersFlow: Flow<List<VideoFolder>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.VIDEO_FOLDERS_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<VideoFolder>>(raw) }.getOrDefault(emptyList())
    }

    val playbackPositionsFlow: Flow<Map<String, PlaybackProgress>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PLAYBACK_POSITIONS_JSON] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, PlaybackProgress>>(raw) }.getOrDefault(emptyMap())
    }

    val lastPlayedUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_PLAYED_URI]
    }

    suspend fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        context.dataStore.edit { prefs ->
            val current = PlayerSettings(
                decoderMode = prefs[Keys.DECODER_MODE].enumOrDefault(DecoderMode.AUTO),
                enableDecoderFallback = prefs[Keys.DECODER_FALLBACK] ?: true,
                enableAudioOffload = prefs[Keys.AUDIO_OFFLOAD] ?: false,
                bufferProfile = prefs[Keys.BUFFER_PROFILE].enumOrDefault(BufferProfile.LITE),
                autoPlay = prefs[Keys.AUTO_PLAY] ?: true,
                resumePlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
                rememberSpeed = prefs[Keys.REMEMBER_SPEED] ?: false,
                defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
                repeatMode = prefs[Keys.REPEAT_MODE].enumOrDefault(RepeatOption.OFF),
                fastSeek = prefs[Keys.FAST_SEEK] ?: true,
                backgroundPlay = prefs[Keys.BACKGROUND_PLAY] ?: false,
                defaultResizeMode = prefs[Keys.DEFAULT_RESIZE_MODE].enumOrDefault(VideoResizeMode.FIT),
                defaultOrientation = prefs[Keys.DEFAULT_ORIENTATION].enumOrDefault(OrientationMode.AUTO),
                showCodecBadge = prefs[Keys.SHOW_CODEC_BADGE] ?: true,
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
                seekIncrementSec = prefs[Keys.SEEK_INCREMENT_SEC] ?: 10,
                controlsTimeoutSec = prefs[Keys.CONTROLS_TIMEOUT_SEC] ?: 4,
                doubleTapSeek = prefs[Keys.DOUBLE_TAP_SEEK] ?: true,
                brightnessGesture = prefs[Keys.BRIGHTNESS_GESTURE] ?: true,
                volumeGesture = prefs[Keys.VOLUME_GESTURE] ?: true,
                seekGesture = prefs[Keys.SEEK_GESTURE] ?: true,
                preferredAudioLang = prefs[Keys.PREFERRED_AUDIO_LANG] ?: "",
                preferredSubtitleLang = prefs[Keys.PREFERRED_SUBTITLE_LANG] ?: "",
                subtitleScale = prefs[Keys.SUBTITLE_SCALE].enumOrDefault(SubtitleScale.MEDIUM),
                appTheme = prefs[Keys.APP_THEME].enumOrDefault(AppThemeMode.DARK),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                librarySort = prefs[Keys.LIBRARY_SORT].enumOrDefault(LibrarySortOption.NAME),
                librarySortAscending = prefs[Keys.LIBRARY_SORT_ASC] ?: true,
            )
            val updated = transform(current)
            prefs[Keys.DECODER_MODE] = updated.decoderMode.name
            prefs[Keys.DECODER_FALLBACK] = updated.enableDecoderFallback
            prefs[Keys.AUDIO_OFFLOAD] = updated.enableAudioOffload
            prefs[Keys.BUFFER_PROFILE] = updated.bufferProfile.name
            prefs[Keys.AUTO_PLAY] = updated.autoPlay
            prefs[Keys.RESUME_PLAYBACK] = updated.resumePlayback
            prefs[Keys.REMEMBER_SPEED] = updated.rememberSpeed
            prefs[Keys.DEFAULT_SPEED] = updated.defaultSpeed
            prefs[Keys.REPEAT_MODE] = updated.repeatMode.name
            prefs[Keys.FAST_SEEK] = updated.fastSeek
            prefs[Keys.BACKGROUND_PLAY] = updated.backgroundPlay
            prefs[Keys.DEFAULT_RESIZE_MODE] = updated.defaultResizeMode.name
            prefs[Keys.DEFAULT_ORIENTATION] = updated.defaultOrientation.name
            prefs[Keys.SHOW_CODEC_BADGE] = updated.showCodecBadge
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.SEEK_INCREMENT_SEC] = updated.seekIncrementSec
            prefs[Keys.CONTROLS_TIMEOUT_SEC] = updated.controlsTimeoutSec
            prefs[Keys.DOUBLE_TAP_SEEK] = updated.doubleTapSeek
            prefs[Keys.BRIGHTNESS_GESTURE] = updated.brightnessGesture
            prefs[Keys.VOLUME_GESTURE] = updated.volumeGesture
            prefs[Keys.SEEK_GESTURE] = updated.seekGesture
            prefs[Keys.PREFERRED_AUDIO_LANG] = updated.preferredAudioLang
            prefs[Keys.PREFERRED_SUBTITLE_LANG] = updated.preferredSubtitleLang
            prefs[Keys.SUBTITLE_SCALE] = updated.subtitleScale.name
            prefs[Keys.APP_THEME] = updated.appTheme.name
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.LIBRARY_SORT] = updated.librarySort.name
            prefs[Keys.LIBRARY_SORT_ASC] = updated.librarySortAscending
        }
    }

    suspend fun addOrUpdateLibraryItem(entry: VideoEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_ITEMS_JSON]?.let {
                runCatching { json.decodeFromString<List<VideoEntry>>(it) }.getOrNull()
            }.orEmpty()
            val updated = listOf(entry) + current.filterNot { it.uri == entry.uri }
            prefs[Keys.LIBRARY_ITEMS_JSON] = json.encodeToString(updated.take(40))
        }
    }

    suspend fun removeLibraryItem(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_ITEMS_JSON]?.let {
                runCatching { json.decodeFromString<List<VideoEntry>>(it) }.getOrNull()
            }.orEmpty()
            prefs[Keys.LIBRARY_ITEMS_JSON] = json.encodeToString(current.filterNot { it.uri == uri })
        }
    }

    suspend fun savePlaybackProgress(uri: String, title: String, positionMs: Long, durationMs: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PLAYBACK_POSITIONS_JSON]?.let {
                runCatching { json.decodeFromString<Map<String, PlaybackProgress>>(it) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()
            current[uri] = PlaybackProgress(
                uri = uri,
                title = title,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAtMs = System.currentTimeMillis(),
            )
            prefs[Keys.PLAYBACK_POSITIONS_JSON] = json.encodeToString(current)
            prefs[Keys.LAST_PLAYED_URI] = uri
        }
    }

    suspend fun addOrUpdateFolder(folder: VideoFolder) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.VIDEO_FOLDERS_JSON]?.let {
                runCatching { json.decodeFromString<List<VideoFolder>>(it) }.getOrNull()
            }.orEmpty()
            val updated = listOf(folder) + current.filterNot { it.treeUri == folder.treeUri }
            prefs[Keys.VIDEO_FOLDERS_JSON] = json.encodeToString(updated.take(25))
        }
    }

    suspend fun removeFolder(treeUri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.VIDEO_FOLDERS_JSON]?.let {
                runCatching { json.decodeFromString<List<VideoFolder>>(it) }.getOrNull()
            }.orEmpty()
            prefs[Keys.VIDEO_FOLDERS_JSON] = json.encodeToString(current.filterNot { it.treeUri == treeUri })
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PLAYBACK_POSITIONS_JSON)
            prefs.remove(Keys.LAST_PLAYED_URI)
            prefs.remove(Keys.LIBRARY_ITEMS_JSON)
            prefs.remove(Keys.VIDEO_FOLDERS_JSON)
        }
    }

    suspend fun resetSettingsToDefaults() {
        val defaults = PlayerSettings()
        updateSettings { defaults }
    }
}

@kotlinx.serialization.Serializable
data class PlaybackProgress(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T {
    if (this == null) return default
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}
