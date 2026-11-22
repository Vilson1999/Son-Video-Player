package com.sonvideo.player.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.sonvideo.player.R
import com.sonvideo.player.data.DecoderMode
import com.sonvideo.player.data.OrientationMode
import com.sonvideo.player.data.PlaybackProgress
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.SettingsRepository
import com.sonvideo.player.data.VideoResizeMode
import com.sonvideo.player.media.MediaInfoHelper
import com.sonvideo.player.media.PlayerFactory
import com.sonvideo.player.ui.components.LivePlaybackTechnicalInfo
import com.sonvideo.player.ui.components.MediaInfoSheet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    uri: String,
    title: String,
    settings: PlayerSettings,
    savedProgress: PlaybackProgress?,
    repository: SettingsRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var playerTuple by remember(uri, settings.decoderMode, settings.enableDecoderFallback, settings.bufferProfile) {
        mutableStateOf(PlayerFactory.createPlayer(context, settings))
    }
    val player = playerTuple.first
    val trackSelector = playerTuple.second

    var isPlaying by remember { mutableStateOf(settings.autoPlay) }
    var isBuffering by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentSpeed by remember { mutableFloatStateOf(if (settings.rememberSpeed) settings.defaultSpeed else 1.0f) }
    var resizeMode by remember { mutableStateOf(settings.defaultResizeMode) }
    var codecBadgeText by remember { mutableStateOf("") }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showMediaInfoSheet by remember { mutableStateOf(false) }

    // Gesture feedback overlays
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var brightnessFeedback by remember { mutableFloatStateOf(-1f) }
    var volumeFeedback by remember { mutableFloatStateOf(-1f) }
    var dragSeekTargetMs by remember { mutableLongStateOf(-1L) }
    var dragSeekDeltaSec by remember { mutableLongStateOf(0L) }

    val configuration = LocalConfiguration.current
    val isInPipMode = activity?.isInPictureInPictureMode == true

    // Apply live settings updates to existing player
    LaunchedEffect(
        player,
        settings.fastSeek,
        settings.repeatMode,
        settings.preferredAudioLang,
        settings.preferredSubtitleLang,
        settings.enableAudioOffload,
        settings.rememberSpeed,
        settings.defaultSpeed,
    ) {
        PlayerFactory.applySettings(player, trackSelector, settings)
        if (settings.rememberSpeed) {
            currentSpeed = settings.defaultSpeed
        }
    }

    // Prepare media item and restore resume position
    LaunchedEffect(player, uri) {
        playbackError = null
        val startPos = if (settings.resumePlayback && savedProgress != null && savedProgress.positionMs > 1500L) {
            if (savedProgress.durationMs > 0 && savedProgress.positionMs > savedProgress.durationMs - 3000L) 0L
            else savedProgress.positionMs
        } else 0L

        player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)), startPos)
        player.playWhenReady = settings.autoPlay
        player.prepare()
    }

    // Player listener for state, codec badge, errors
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                    playbackError = null
                }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onTracksChanged(tracks: Tracks) {
                codecBadgeText = buildCodecBadge(tracks, settings.decoderMode)
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = error.localizedMessage ?: "Playback error (${error.errorCodeName})"
            }
        }
        player.addListener(listener)
        onDispose {
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            if (settings.resumePlayback && dur > 0L) {
                scope.launch { repository.savePlaybackProgress(uri, title, pos, dur) }
            }
            player.removeListener(listener)
            player.release()
        }
    }

    // Poll playback position
    LaunchedEffect(player, isPlaying) {
        while (true) {
            if (!isScrubbing && dragSeekTargetMs < 0L) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                val d = player.duration
                if (d > 0L) durationMs = d
            }
            delay(300L)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isLocked, showSpeedMenu, showAudioSheet, showSubtitleSheet, showMediaInfoSheet) {
        if (showControls && isPlaying && !isLocked && !showSpeedMenu && !showAudioSheet && !showSubtitleSheet && !showMediaInfoSheet) {
            delay(settings.controlsTimeoutSec * 1000L)
            showControls = false
        }
    }

    // Immersive system bars toggle
    DisposableEffect(activity, showControls, isInPipMode) {
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showControls && !isInPipMode) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Apply orientation setting
    DisposableEffect(activity, settings.defaultOrientation, videoSize) {
        if (activity != null) {
            activity.requestedOrientation = when (settings.defaultOrientation) {
                OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                OrientationMode.MATCH_VIDEO -> {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        if (videoSize.width >= videoSize.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Restore system brightness when exiting player
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { win ->
                val attrs = win.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = attrs
            }
        }
    }

    // Lifecycle pause/save progress
    DisposableEffect(lifecycleOwner, player, settings.backgroundPlay) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    if (settings.resumePlayback && dur > 0L) {
                        scope.launch { repository.savePlaybackProgress(uri, title, pos, dur) }
                    }
                    if (!settings.backgroundPlay && activity?.isInPictureInPictureMode != true) {
                        player.pause()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        if (settings.resumePlayback && dur > 0L) {
            scope.launch { repository.savePlaybackProgress(uri, title, pos, dur) }
        }
        onBack()
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it },
    ) {
        // Video surface via Media3 PlayerView (Compose HUD overlay)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.argb(150, 10, 12, 18),
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                android.graphics.Color.BLACK,
                                null,
                            )
                        )
                    }
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
                view.keepScreenOn = settings.keepScreenOn && isPlaying
                view.resizeMode = when (resizeMode) {
                    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                view.subtitleView?.setFractionalTextSize(settings.subtitleScale.fraction)
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Interactive Gesture Layer (Tap, Double-Tap Seek, Swipe Left/Right Seek, Left Brightness, Right Volume)
        if (!isInPipMode) {
            val latestLocked by rememberUpdatedState(isLocked)
            val latestSettings by rememberUpdatedState(settings)
            val latestDurationMs by rememberUpdatedState(durationMs)
            val latestContainerSize by rememberUpdatedState(containerSize)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_gesture_layer")
                    .pointerInput(player) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val curSettings = latestSettings
                                val width = latestContainerSize.width
                                if (!latestLocked && curSettings.doubleTapSeek && width > 0) {
                                    val deltaMs = curSettings.seekIncrementSec * 1000L
                                    val isRight = offset.x >= width / 2f
                                    val dur = latestDurationMs
                                    val maxTarget = if (dur > 0L) dur else Long.MAX_VALUE
                                    val target = if (isRight) {
                                        (player.currentPosition + deltaMs).coerceAtMost(maxTarget)
                                    } else {
                                        (player.currentPosition - deltaMs).coerceAtLeast(0L)
                                    }
                                    player.seekTo(target)
                                    positionMs = target
                                    scope.launch {
                                        seekFeedbackText = if (isRight) "+${curSettings.seekIncrementSec}s" else "-${curSettings.seekIncrementSec}s"
                                        delay(750L)
                                        seekFeedbackText = null
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(player) {
                        var mode = 0 // 0=none, 1=horizontal seek, 2=brightness (left), 3=volume (right)
                        var startBrightness = 0.5f
                        var startVolume = 0
                        var dragStartX = 0f
                        var accumulatedDx = 0f
                        var accumulatedDy = 0f
                        var startPosMs = 0L

                        detectDragGestures(
                            onDragStart = { offset ->
                                if (latestLocked) return@detectDragGestures
                                mode = 0
                                dragStartX = offset.x
                                accumulatedDx = 0f
                                accumulatedDy = 0f
                                startPosMs = player.currentPosition.coerceAtLeast(0L)
                                val lpBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                startBrightness = if (lpBrightness in 0f..1f) {
                                    lpBrightness
                                } else {
                                    val sys = runCatching {
                                        Settings.System.getInt(
                                            context.contentResolver,
                                            Settings.System.SCREEN_BRIGHTNESS,
                                            128,
                                        )
                                    }.getOrDefault(128)
                                    (sys / 255f).coerceIn(0.05f, 1.0f)
                                }
                                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            },
                            onDragEnd = {
                                if (mode == 1 && dragSeekTargetMs >= 0L) {
                                    player.seekTo(dragSeekTargetMs)
                                    positionMs = dragSeekTargetMs
                                }
                                mode = 0
                                dragSeekTargetMs = -1L
                                dragSeekDeltaSec = 0L
                                brightnessFeedback = -1f
                                volumeFeedback = -1f
                            },
                            onDragCancel = {
                                mode = 0
                                dragSeekTargetMs = -1L
                                dragSeekDeltaSec = 0L
                                brightnessFeedback = -1f
                                volumeFeedback = -1f
                            },
                            onDrag = { change, dragAmount ->
                                val size = latestContainerSize
                                val curSettings = latestSettings
                                val dur = latestDurationMs
                                if (latestLocked || size.width <= 0 || size.height <= 0) return@detectDragGestures
                                change.consume()
                                accumulatedDx += dragAmount.x
                                accumulatedDy += dragAmount.y

                                if (mode == 0 && (abs(accumulatedDx) > 18f || abs(accumulatedDy) > 18f)) {
                                    mode = if (abs(accumulatedDx) > abs(accumulatedDy)) {
                                        if (curSettings.seekGesture && dur > 0L) 1 else 0
                                    } else {
                                        val isLeft = dragStartX < size.width / 2f
                                        if (isLeft && curSettings.brightnessGesture) 2
                                        else if (!isLeft && curSettings.volumeGesture) 3
                                        else 0
                                    }
                                }

                                when (mode) {
                                    1 -> {
                                        val deltaRatio = accumulatedDx / size.width.toFloat()
                                        val seekWindow = minOf(dur, 120_000L).coerceAtLeast(15_000L)
                                        val target = (startPosMs + (deltaRatio * seekWindow).toLong())
                                            .coerceIn(0L, dur.coerceAtLeast(0L))
                                        dragSeekTargetMs = target
                                        dragSeekDeltaSec = (target - startPosMs) / 1000L
                                    }
                                    2 -> {
                                        val delta = -accumulatedDy / (size.height * 0.65f)
                                        val next = (startBrightness + delta).coerceIn(0.01f, 1.0f)
                                        activity?.window?.let { win ->
                                            val attrs = win.attributes
                                            attrs.screenBrightness = next
                                            win.attributes = attrs
                                        }
                                        brightnessFeedback = next
                                    }
                                    3 -> {
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val delta = (-accumulatedDy / (size.height * 0.65f)) * maxVol
                                        val next = (startVolume + delta).roundToInt().coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                        volumeFeedback = next.toFloat() / maxVol.toFloat()
                                    }
                                }
                            },
                        )
                    },
            )
        }

        // Buffering Indicator
        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.5.dp,
            )
        }

        // Gesture Feedback Pills
        seekFeedbackText?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }

        if (dragSeekTargetMs >= 0L) {
            val signedDelta = if (dragSeekDeltaSec >= 0L) "+${dragSeekDeltaSec}s" else "${dragSeekDeltaSec}s"
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("gesture_seek_overlay"),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${MediaInfoHelper.formatDuration(dragSeekTargetMs)} / ${MediaInfoHelper.formatDuration(durationMs)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = signedDelta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (brightnessFeedback >= 0f) {
            GestureMeterPill(
                icon = Icons.Rounded.Brightness6,
                label = "${(brightnessFeedback * 100).toInt()}%",
                progress = brightnessFeedback,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp),
            )
        }

        if (volumeFeedback >= 0f) {
            GestureMeterPill(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = "${(volumeFeedback * 100).toInt()}%",
                progress = volumeFeedback,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp),
            )
        }

        // Error overlay with NextLib fallback retry
        playbackError?.let { errorMsg ->
            Surface(
                color = Color(0xE6131722),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Playback Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onBack) {
                            Text("Back", color = Color.White)
                        }
                        Button(
                            onClick = {
                                playbackError = null
                                player.prepare()
                                player.play()
                            },
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Lock-only floating unlock pill
        if (isLocked && showControls && !isInPipMode) {
            FilledTonalIconButton(
                onClick = { isLocked = false },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .statusBarsPadding()
                    .padding(start = 20.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("unlock_controls_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = stringResource(R.string.unlock_controls),
                )
            }
        }

        // Full Player HUD Controls
        AnimatedVisibility(
            visible = showControls && !isLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.80f),
                            )
                        )
                    ),
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(
                        onClick = {
                            val pos = player.currentPosition.coerceAtLeast(0L)
                            val dur = player.duration.coerceAtLeast(0L)
                            if (settings.resumePlayback && dur > 0L) {
                                scope.launch { repository.savePlaybackProgress(uri, title, pos, dur) }
                            }
                            onBack()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (settings.showCodecBadge && codecBadgeText.isNotBlank()) {
                            Text(
                                text = codecBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = { showMediaInfoSheet = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_mediainfo_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.media_info),
                        )
                    }

                    IconButton(
                        onClick = { isLocked = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_lock_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.lock_controls),
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = Color.White,
                        )
                    }
                }

                // Center Transport Controls
                if (playbackError == null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        IconButton(
                            onClick = {
                                val target = (player.currentPosition - settings.seekIncrementSec * 1000L).coerceAtLeast(0L)
                                player.seekTo(target)
                                positionMs = target
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("player_rewind_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = stringResource(R.string.rewind),
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("player_play_pause_button"),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                                modifier = Modifier.size(40.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                val maxTarget = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                                val target = (player.currentPosition + settings.seekIncrementSec * 1000L)
                                    .coerceAtMost(maxTarget)
                                player.seekTo(target)
                                positionMs = target
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("player_forward_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = stringResource(R.string.fast_forward),
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }

                // Bottom Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val displayedPos = if (isScrubbing) scrubPositionMs else positionMs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = MediaInfoHelper.formatDuration(displayedPos),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Slider(
                            value = if (durationMs > 0L) displayedPos.toFloat() / durationMs.toFloat() else 0f,
                            onValueChange = { fraction ->
                                isScrubbing = true
                                scrubPositionMs = (fraction * durationMs).toLong()
                            },
                            onValueChangeFinished = {
                                player.seekTo(scrubPositionMs)
                                positionMs = scrubPositionMs
                                isScrubbing = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_seekbar"),
                        )
                        Text(
                            text = MediaInfoHelper.formatDuration(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Surface(
                                onClick = { showSpeedMenu = true },
                                color = Color.White.copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_speed_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = stringResource(R.string.playback_speed),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.2gx", currentSpeed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            currentSpeed = speed
                                            player.setPlaybackSpeed(speed)
                                            showSpeedMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { showAudioSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_audio_tracks_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Audiotrack,
                                    contentDescription = stringResource(R.string.audio_tracks),
                                    tint = Color.White,
                                )
                            }

                            IconButton(
                                onClick = { showSubtitleSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_subtitles_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = stringResource(R.string.subtitles),
                                    tint = Color.White,
                                )
                            }

                            IconButton(
                                onClick = {
                                    val modes = VideoResizeMode.entries
                                    resizeMode = modes[(modes.indexOf(resizeMode) + 1) % modes.size]
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_resize_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AspectRatio,
                                    contentDescription = stringResource(R.string.aspect_ratio),
                                    tint = Color.White,
                                )
                            }

                            IconButton(
                                onClick = {
                                    activity?.let { act ->
                                        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                        act.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_rotate_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ScreenRotation,
                                    contentDescription = stringResource(R.string.rotate_screen),
                                    tint = Color.White,
                                )
                            }

                            IconButton(
                                onClick = {
                                    activity?.let { act ->
                                        val ratio = if (videoSize.width > 0 && videoSize.height > 0) {
                                            Rational(videoSize.width, videoSize.height)
                                        } else {
                                            Rational(16, 9)
                                        }
                                        val pipParams = PictureInPictureParams.Builder()
                                            .setAspectRatio(ratio)
                                            .build()
                                        act.enterPictureInPictureMode(pipParams)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_pip_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.picture_in_picture),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioSheet) {
        TrackPickerSheet(
            title = "Audio Tracks",
            trackType = C.TRACK_TYPE_AUDIO,
            player = player,
            onDismiss = { showAudioSheet = false },
        )
    }

    if (showSubtitleSheet) {
        TrackPickerSheet(
            title = "Subtitle Tracks",
            trackType = C.TRACK_TYPE_TEXT,
            player = player,
            onDismiss = { showSubtitleSheet = false },
        )
    }

    if (showMediaInfoSheet) {
        val liveInfo = remember(uri, videoSize, durationMs, player.currentTracks, settings.decoderMode) {
            buildLiveTechnicalInfo(
                context = context,
                uriString = uri,
                tracks = player.currentTracks,
                videoSize = videoSize,
                durationMs = durationMs,
                decoderMode = settings.decoderMode,
            )
        }
        MediaInfoSheet(
            title = title,
            uriString = uri,
            liveInfo = liveInfo,
            onDismiss = { showMediaInfoSheet = false },
        )
    }
}

@Composable
private fun GestureMeterPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .width(64.dp)
                    .height(5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

private fun buildLiveTechnicalInfo(
    context: Context,
    uriString: String,
    tracks: Tracks,
    videoSize: VideoSize,
    durationMs: Long,
    decoderMode: DecoderMode,
): LivePlaybackTechnicalInfo {
    var vWidth = videoSize.width
    var vHeight = videoSize.height
    var videoCodec = ""
    var frameRate = ""
    var videoBitrate = ""
    var audioCodec = ""
    var audioDetails = ""

    for (group in tracks.groups) {
        if (!group.isSelected) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val fmt = group.getTrackFormat(i)
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    if (fmt.width > 0 && fmt.height > 0) {
                        vWidth = fmt.width
                        vHeight = fmt.height
                    }
                    videoCodec = (fmt.codecs ?: fmt.sampleMimeType?.substringAfter('/'))
                        ?.uppercase(Locale.US)
                        .orEmpty()
                    if (fmt.frameRate > 0f) {
                        frameRate = String.format(Locale.US, "%.2f fps", fmt.frameRate)
                    }
                    if (fmt.bitrate > 0) {
                        videoBitrate = "${fmt.bitrate / 1000} kbps"
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    audioCodec = (fmt.codecs ?: fmt.sampleMimeType?.substringAfter('/'))
                        ?.uppercase(Locale.US)
                        .orEmpty()
                    val ch = if (fmt.channelCount > 0) "${fmt.channelCount} ch" else ""
                    val sr = if (fmt.sampleRate > 0) "${fmt.sampleRate} Hz" else ""
                    audioDetails = listOf(ch, sr).filter { it.isNotBlank() }.joinToString(" • ")
                }
            }
        }
    }

    val resolution = if (vWidth > 0 && vHeight > 0) "${vWidth}×${vHeight}" else ""
    val fileSize = runCatching {
        MediaInfoHelper.resolveFileDetails(context, Uri.parse(uriString)).sizeBytes
    }.getOrDefault(0L)
    val engineTag = when (decoderMode) {
        DecoderMode.AUTO -> "NextLib Auto"
        DecoderMode.PREFER_FFMPEG -> "NextLib FFmpeg"
        DecoderMode.PLATFORM_ONLY -> "Platform"
    }

    return LivePlaybackTechnicalInfo(
        resolution = resolution,
        videoCodec = videoCodec,
        frameRate = frameRate,
        videoBitrate = videoBitrate,
        audioCodec = audioCodec,
        audioDetails = audioDetails,
        durationMs = durationMs,
        fileSizeBytes = fileSize,
        decoderEngine = engineTag,
    )
}

private fun buildCodecBadge(tracks: Tracks, decoderMode: DecoderMode): String {
    var videoSummary = ""
    var audioSummary = ""
    for (group in tracks.groups) {
        if (!group.isSelected) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val fmt = group.getTrackFormat(i)
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    val res = if (fmt.height > 0) "${fmt.height}p" else ""
                    val codec = fmt.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""
                    val fps = if (fmt.frameRate > 0f) "${fmt.frameRate.toInt()}fps" else ""
                    videoSummary = listOf(res, codec, fps).filter { it.isNotBlank() }.joinToString(" • ")
                }
                C.TRACK_TYPE_AUDIO -> {
                    val codec = fmt.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""
                    audioSummary = codec
                }
            }
        }
    }
    val engineTag = when (decoderMode) {
        DecoderMode.AUTO -> "NextLib Auto"
        DecoderMode.PREFER_FFMPEG -> "NextLib FFmpeg"
        DecoderMode.PLATFORM_ONLY -> "Platform"
    }
    return listOf(videoSummary, audioSummary, engineTag).filter { it.isNotBlank() }.joinToString(" | ")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
