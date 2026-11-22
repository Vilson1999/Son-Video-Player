package com.sonvideo.player.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sonvideo.player.data.DecoderMode
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.RepeatOption
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

object PlayerFactory {

    @OptIn(UnstableApi::class)
    fun createPlayer(context: Context, settings: PlayerSettings): Pair<ExoPlayer, DefaultTrackSelector> {
        val extensionMode = when (settings.decoderMode) {
            DecoderMode.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.PREFER_FFMPEG -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            DecoderMode.PLATFORM_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }

        val renderersFactory = NextRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(extensionMode)
            .setEnableDecoderFallback(settings.enableDecoderFallback)

        val bufferProfile = settings.bufferProfile
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferProfile.minBufferMs,
                bufferProfile.maxBufferMs,
                bufferProfile.bufferForPlaybackMs,
                bufferProfile.bufferForPlaybackAfterRebufferMs,
            )
            .build()

        val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
            parameters = buildTrackParameters(parameters, settings)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                applySettings(this, trackSelector, settings)
            }

        return player to trackSelector
    }

    @OptIn(UnstableApi::class)
    fun applySettings(
        player: ExoPlayer,
        trackSelector: DefaultTrackSelector,
        settings: PlayerSettings,
    ) {
        trackSelector.parameters = buildTrackParameters(trackSelector.parameters, settings)
        player.setSeekParameters(
            if (settings.fastSeek) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT
        )
        player.repeatMode = when (settings.repeatMode) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            RepeatOption.ALL -> Player.REPEAT_MODE_ALL
        }
        if (settings.rememberSpeed) {
            player.setPlaybackSpeed(settings.defaultSpeed)
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildTrackParameters(
        current: DefaultTrackSelector.Parameters,
        settings: PlayerSettings,
    ): DefaultTrackSelector.Parameters {
        val builder = current.buildUpon()

        if (settings.preferredAudioLang.isNotBlank()) {
            builder.setPreferredAudioLanguage(settings.preferredAudioLang)
        } else {
            builder.setPreferredAudioLanguages()
        }

        when (settings.preferredSubtitleLang) {
            "off" -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            "" -> {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.setPreferredTextLanguages()
            }
            else -> {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.setPreferredTextLanguage(settings.preferredSubtitleLang)
            }
        }

        val offloadMode = if (settings.enableAudioOffload) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        builder.setAudioOffloadPreferences(
            AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(offloadMode)
                .build()
        )

        return builder.build()
    }
}
