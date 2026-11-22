package com.sonvideo.player.data

import kotlinx.serialization.Serializable

enum class DecoderMode(val label: String, val description: String) {
    AUTO("Platform + NextLib Fallback", "Uses hardware decoders first, falls back to NextLib FFmpeg"),
    PREFER_FFMPEG("Prefer NextLib FFmpeg", "Prioritizes FFmpeg & dav1d software decoders for all tracks"),
    PLATFORM_ONLY("Platform Decoders Only", "Uses Android MediaCodec hardware decoders exclusively"),
}

enum class BufferProfile(
    val label: String,
    val description: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
) {
    LITE("Lite (Low RAM)", "Fast startup, minimal memory footprint", 5_000, 15_000, 1_000, 2_000),
    BALANCED("Balanced", "Recommended for local & HD videos", 15_000, 50_000, 2_000, 4_000),
    HIGH_BITRATE("High Buffer", "Smooth playback for large 4K / high-bitrate local videos", 25_000, 90_000, 2_500, 5_000),
}

enum class VideoResizeMode(val label: String) {
    FIT("Fit"),
    ZOOM("Crop / Zoom"),
    FILL("Stretch / Fill"),
}

enum class OrientationMode(val label: String) {
    AUTO("Auto (Sensor)"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    MATCH_VIDEO("Match Video Aspect"),
}

enum class RepeatOption(val label: String) {
    OFF("Off"),
    ONE("Loop Current"),
    ALL("Loop All"),
}

enum class SubtitleScale(val label: String, val fraction: Float) {
    SMALL("Small", 0.042f),
    MEDIUM("Medium", 0.055f),
    LARGE("Large", 0.072f),
    EXTRA_LARGE("Extra Large", 0.09f),
}

enum class AppThemeMode(val label: String) {
    DARK("Obsidian Dark"),
    LIGHT("Daylight Light"),
    SYSTEM("Follow System"),
}

enum class LibrarySortOption(val label: String) {
    NAME("Name"),
    DATE_ADDED("Date Added"),
    FILE_SIZE("File Size"),
}

data class PlayerSettings(
    // NextLib Decoder & Engine
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val enableDecoderFallback: Boolean = true,
    val enableAudioOffload: Boolean = false,
    val bufferProfile: BufferProfile = BufferProfile.LITE,

    // Playback
    val autoPlay: Boolean = true,
    val resumePlayback: Boolean = true,
    val rememberSpeed: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val repeatMode: RepeatOption = RepeatOption.OFF,
    val fastSeek: Boolean = true,
    val backgroundPlay: Boolean = false,

    // Display & Video
    val defaultResizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val defaultOrientation: OrientationMode = OrientationMode.AUTO,
    val showCodecBadge: Boolean = true,
    val keepScreenOn: Boolean = true,

    // Controls & Gestures
    val seekIncrementSec: Int = 10,
    val controlsTimeoutSec: Int = 4,
    val doubleTapSeek: Boolean = true,
    val brightnessGesture: Boolean = true,
    val volumeGesture: Boolean = true,
    val seekGesture: Boolean = true,

    // Audio & Subtitles
    val preferredAudioLang: String = "",
    val preferredSubtitleLang: String = "",
    val subtitleScale: SubtitleScale = SubtitleScale.MEDIUM,

    // Appearance & Library
    val appTheme: AppThemeMode = AppThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val librarySort: LibrarySortOption = LibrarySortOption.NAME,
    val librarySortAscending: Boolean = true,
)

@Serializable
data class VideoEntry(
    val uri: String,
    val title: String,
    val subtitle: String = "",
    val formatTag: String = "VIDEO",
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val addedAtMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val folderUri: String = "",
    val folderName: String = "Device Videos",
)

@Serializable
data class VideoFolder(
    val treeUri: String,
    val name: String,
    val addedAtMs: Long = 0L,
)
