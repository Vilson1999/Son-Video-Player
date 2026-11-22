package com.sonvideo.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sonvideo.player.media.InspectedMediaInfo
import com.sonvideo.player.media.MediaInfoHelper
import java.util.Locale

data class LivePlaybackTechnicalInfo(
    val resolution: String = "",
    val videoCodec: String = "",
    val frameRate: String = "",
    val videoBitrate: String = "",
    val audioCodec: String = "",
    val audioDetails: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val decoderEngine: String = "",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaInfoSheet(
    title: String,
    uriString: String,
    onDismiss: () -> Unit,
    liveInfo: LivePlaybackTechnicalInfo? = null,
) {
    val context = LocalContext.current
    var isLoading by remember(uriString) { mutableStateOf(true) }
    var infoResult by remember(uriString) { mutableStateOf<Result<InspectedMediaInfo>?>(null) }

    LaunchedEffect(uriString) {
        isLoading = true
        infoResult = MediaInfoHelper.inspect(context, uriString)
        isLoading = false
    }

    val probed = infoResult?.getOrNull()
    val firstVideo = probed?.videoStreams?.firstOrNull()
    val firstAudio = probed?.audioStreams?.firstOrNull()

    val resolvedResolution = when {
        firstVideo != null && firstVideo.width > 0 && firstVideo.height > 0 ->
            "${firstVideo.width}×${firstVideo.height}"
        !liveInfo?.resolution.isNullOrBlank() -> liveInfo!!.resolution
        else -> "--"
    }
    val resolvedVideoCodec = firstVideo?.codec?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
        ?: liveInfo?.videoCodec?.takeIf { it.isNotBlank() }
        ?: "--"
    val resolvedDurationMs = probed?.durationMs?.takeIf { it > 0L }
        ?: liveInfo?.durationMs?.takeIf { it > 0L }
        ?: 0L
    val resolvedFps = when {
        firstVideo != null && firstVideo.frameRate > 0.0 ->
            String.format(Locale.US, "%.2f fps", firstVideo.frameRate)
        !liveInfo?.frameRate.isNullOrBlank() -> liveInfo!!.frameRate
        else -> null
    }
    val resolvedBitrate = when {
        firstVideo != null && firstVideo.bitRate > 0L -> "${firstVideo.bitRate / 1000} kbps"
        !liveInfo?.videoBitrate.isNullOrBlank() -> liveInfo!!.videoBitrate
        else -> null
    }
    val resolvedAudioCodec = firstAudio?.codec?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
        ?: liveInfo?.audioCodec?.takeIf { it.isNotBlank() }
        ?: null
    val resolvedSize = liveInfo?.fileSizeBytes?.takeIf { it > 0L }
        ?.let { MediaInfoHelper.formatFileSize(it) }
    val resolvedContainer = probed?.format?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
        ?: MediaInfoHelper.detectFormatTag(title)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("media_info_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Technical Video Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("media_info_summary_chips"),
            ) {
                TechChip(label = "RESOLUTION", value = resolvedResolution)
                TechChip(label = "VIDEO CODEC", value = resolvedVideoCodec)
                TechChip(label = "DURATION", value = MediaInfoHelper.formatDuration(resolvedDurationMs))
                TechChip(label = "CONTAINER", value = resolvedContainer)
                resolvedFps?.let { TechChip(label = "FRAME RATE", value = it) }
                resolvedBitrate?.let { TechChip(label = "BITRATE", value = it) }
                resolvedAudioCodec?.let { TechChip(label = "AUDIO CODEC", value = it) }
                resolvedSize?.let { TechChip(label = "FILE SIZE", value = it) }
                liveInfo?.decoderEngine?.takeIf { it.isNotBlank() }?.let {
                    TechChip(label = "ENGINE", value = it)
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "Inspecting container streams...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (probed != null) {
                if (probed.videoStreams.isNotEmpty()) {
                    Text(
                        text = "Video Tracks",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    probed.videoStreams.forEach { v ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(
                                        text = "Track #${v.index} • ${v.codec.uppercase(Locale.US)}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    val fps = if (v.frameRate > 0) String.format(Locale.US, "%.2f fps", v.frameRate) else "Variable fps"
                                    val kbps = if (v.bitRate > 0) " • ${v.bitRate / 1000} kbps" else ""
                                    Text(
                                        text = "${v.width}×${v.height} • $fps$kbps",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (probed.audioStreams.isNotEmpty()) {
                    Text(
                        text = "Audio Tracks",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    probed.audioStreams.forEach { a ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Column {
                                    val lang = a.language?.uppercase(Locale.US) ?: "UND"
                                    Text(
                                        text = "Track #${a.index} • ${a.codec.uppercase(Locale.US)} ($lang)",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "${a.channels} ch • ${a.sampleRate} Hz" +
                                            (a.title?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (probed.subtitleStreams.isNotEmpty()) {
                    Text(
                        text = "Subtitle Tracks",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    probed.subtitleStreams.forEach { s ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.ClosedCaption, contentDescription = null)
                                Text(
                                    text = "#${s.index} • ${s.codec.uppercase(Locale.US)} • ${s.language?.uppercase(Locale.US) ?: "UND"}" +
                                        (s.title?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TechChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
