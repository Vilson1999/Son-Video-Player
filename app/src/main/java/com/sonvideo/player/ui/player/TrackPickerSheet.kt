package com.sonvideo.player.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerSheet(
    title: String,
    trackType: Int,
    player: Player,
    onDismiss: () -> Unit,
) {
    val tracks: Tracks = player.currentTracks
    val groups = tracks.groups.filter { it.type == trackType }
    val params = player.trackSelectionParameters
    val isDisabled = params.disabledTrackTypes.contains(trackType)
    val hasManualOverride = groups.any { params.overrides.containsKey(it.mediaTrackGroup) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Auto option
            TrackOptionRow(
                label = "Auto (Recommended)",
                subtitle = "Let engine pick best track",
                selected = !isDisabled && !hasManualOverride,
                onClick = {
                    player.trackSelectionParameters = params.buildUpon()
                        .setTrackTypeDisabled(trackType, false)
                        .clearOverridesOfType(trackType)
                        .build()
                    onDismiss()
                },
            )

            // Off option
            TrackOptionRow(
                label = "Off / Disabled",
                subtitle = null,
                selected = isDisabled,
                onClick = {
                    player.trackSelectionParameters = params.buildUpon()
                        .setTrackTypeDisabled(trackType, true)
                        .build()
                    onDismiss()
                },
            )

            groups.forEachIndexed { groupIdx, group ->
                for (trackIdx in 0 until group.length) {
                    val format = group.getTrackFormat(trackIdx)
                    val isSupported = group.isTrackSupported(trackIdx)
                    val isSelected = !isDisabled && hasManualOverride && group.isTrackSelected(trackIdx)

                    val name = buildString {
                        if (!format.label.isNullOrBlank()) {
                            append(format.label)
                        } else if (!format.language.isNullOrBlank() && format.language != C.LANGUAGE_UNDETERMINED) {
                            append(format.language!!.uppercase())
                        } else {
                            append("Track ${groupIdx + 1}.${trackIdx + 1}")
                        }
                    }

                    val details = buildList {
                        format.sampleMimeType?.substringAfter('/')?.uppercase()?.let { add(it) }
                        if (format.channelCount > 0) add("${format.channelCount}ch")
                        if (format.sampleRate > 0) add("${format.sampleRate}Hz")
                        if (format.bitrate > 0) add("${format.bitrate / 1000}kbps")
                        if (!isSupported) add("Unsupported")
                    }.joinToString(" • ")

                    TrackOptionRow(
                        label = name,
                        subtitle = details.ifBlank { null },
                        selected = isSelected,
                        enabled = isSupported,
                        onClick = {
                            player.trackSelectionParameters = params.buildUpon()
                                .setTrackTypeDisabled(trackType, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, trackIdx)
                                )
                                .build()
                            onDismiss()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackOptionRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = if (enabled) onClick else null, enabled = enabled)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
