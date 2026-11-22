package com.sonvideo.player.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonvideo.player.R
import com.sonvideo.player.data.LibrarySortOption
import com.sonvideo.player.data.PlaybackProgress
import com.sonvideo.player.data.VideoEntry
import com.sonvideo.player.media.MediaInfoHelper
import com.sonvideo.player.media.VideoThumbnailCache

data class FolderDisplayGroup(
    val id: String,
    val treeUri: String?,
    val name: String,
    val videos: List<VideoEntry>,
    val totalSizeBytes: Long,
    val latestAddedAtMs: Long,
)

fun sortFolders(
    folders: List<FolderDisplayGroup>,
    sort: LibrarySortOption,
    ascending: Boolean,
): List<FolderDisplayGroup> {
    val comparator = when (sort) {
        LibrarySortOption.NAME -> compareBy<FolderDisplayGroup> { it.name.lowercase() }
        LibrarySortOption.DATE_ADDED -> compareBy<FolderDisplayGroup> { it.latestAddedAtMs }
        LibrarySortOption.FILE_SIZE -> compareBy<FolderDisplayGroup> { it.totalSizeBytes }
    }
    return if (ascending) folders.sortedWith(comparator) else folders.sortedWith(comparator.reversed())
}

fun sortVideos(
    videos: List<VideoEntry>,
    sort: LibrarySortOption,
    ascending: Boolean,
): List<VideoEntry> {
    val comparator = when (sort) {
        LibrarySortOption.NAME -> compareBy<VideoEntry> { it.title.lowercase() }
        LibrarySortOption.DATE_ADDED -> compareBy<VideoEntry> { it.addedAtMs }
        LibrarySortOption.FILE_SIZE -> compareBy<VideoEntry> { it.sizeBytes }
    }
    return if (ascending) videos.sortedWith(comparator) else videos.sortedWith(comparator.reversed())
}

@Composable
fun CollectionSortBar(
    title: String,
    countText: String,
    sortOption: LibrarySortOption,
    ascending: Boolean,
    menuExpanded: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onSelectSort: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        SortMenuButton(
            sortOption = sortOption,
            ascending = ascending,
            menuExpanded = menuExpanded,
            onToggleMenu = onToggleMenu,
            onSelectSort = onSelectSort,
        )
    }
}

@Composable
fun SortMenuButton(
    sortOption: LibrarySortOption,
    ascending: Boolean,
    menuExpanded: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onSelectSort: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            onClick = { onToggleMenu(true) },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("sort_menu_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = stringResource(R.string.sort_by),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = sortOption.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Icon(
                    imageVector = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = if (ascending) "Ascending" else "Descending",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onToggleMenu(false) },
            modifier = Modifier.testTag("sort_dropdown_menu"),
        ) {
            LibrarySortOption.entries.forEach { option ->
                val isSelected = sortOption == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = { onSelectSort(option) },
                    modifier = Modifier.testTag("sort_option_${option.name.lowercase()}"),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (ascending) "Switch to Descending" else "Switch to Ascending") },
                trailingIcon = {
                    Icon(
                        imageVector = if (ascending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = { onSelectSort(sortOption) },
                modifier = Modifier.testTag("sort_toggle_direction"),
            )
        }
    }
}

@Composable
fun VideoEntryCard(
    entry: VideoEntry,
    progress: PlaybackProgress?,
    onPlay: () -> Unit,
    onInspect: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val sizeText = if (entry.sizeBytes > 0L) MediaInfoHelper.formatFileSize(entry.sizeBytes) else null
    val dateText = MediaInfoHelper.formatShortDate(entry.addedAtMs).takeIf { it.isNotBlank() }
    val detailText = listOfNotNull(sizeText, dateText).joinToString(" • ")

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("video_card_${entry.title}"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoThumbnailPreview(
                    uri = entry.uri,
                    contentDescription = entry.title,
                    showPlayBadge = true,
                    modifier = Modifier
                        .width(96.dp)
                        .height(56.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = entry.formatTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (detailText.isNotBlank()) {
                            Text(
                                text = detailText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onInspect,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.media_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (progress != null && progress.durationMs > 0L && progress.positionMs > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                val fraction = (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
fun VideoThumbnailPreview(
    uri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showPlayBadge: Boolean = false,
    overlayIcon: ImageVector? = null,
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = VideoThumbnailCache.getCached(uri),
        key1 = uri,
    ) {
        if (value == null) {
            value = VideoThumbnailCache.loadThumbnail(context, uri)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .testTag("video_thumbnail"),
    ) {
        Crossfade(
            targetState = thumbnail,
            label = "video_thumbnail_crossfade",
        ) { bitmap ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (showPlayBadge || overlayIcon != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.45f),
                                        )
                                    )
                                ),
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.56f),
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = overlayIcon ?: Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = overlayIcon ?: Icons.Rounded.Movie,
                            contentDescription = contentDescription,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
