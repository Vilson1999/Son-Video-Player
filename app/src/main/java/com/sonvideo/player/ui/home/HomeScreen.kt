package com.sonvideo.player.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonvideo.player.R
import com.sonvideo.player.data.LibrarySortOption
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.SettingsRepository
import com.sonvideo.player.data.VideoEntry
import com.sonvideo.player.data.VideoFolder
import com.sonvideo.player.media.MediaInfoHelper
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    settings: PlayerSettings,
    libraryItems: List<VideoEntry>,
    folders: List<VideoFolder>,
    repository: SettingsRepository,
    onOpenFolder: (folderName: String, treeUri: String?) -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scannedByFolder by remember { mutableStateOf<Map<String, List<VideoEntry>>>(emptyMap()) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(folders) {
        val updated = linkedMapOf<String, List<VideoEntry>>()
        for (folder in folders) {
            val videos = MediaInfoHelper.scanFolderVideos(
                context = context,
                treeUri = Uri.parse(folder.treeUri),
                rootFolderName = folder.name,
            )
            updated[folder.treeUri] = videos
        }
        scannedByFolder = updated
    }

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
            scope.launch { repository.addOrUpdateFolder(folder) }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val meta = MediaInfoHelper.resolveFileDetails(context, uri)
            val entry = VideoEntry(
                uri = uri.toString(),
                title = meta.displayName,
                subtitle = meta.folderName,
                formatTag = MediaInfoHelper.detectFormatTag(meta.displayName),
                addedAtMs = meta.dateAddedMs,
                sizeBytes = meta.sizeBytes,
                folderName = meta.folderName,
            )
            scope.launch { repository.addOrUpdateLibraryItem(entry) }
            onPlayVideo(entry.uri, entry.title)
        }
    }

    val launchFolderPicker = { folderPickerLauncher.launch(null) }
    val launchFilePicker = {
        filePickerLauncher.launch(
            arrayOf(
                "video/*",
                "application/x-matroska",
                "application/octet-stream",
            )
        )
    }

    val rawGroups = remember(folders, scannedByFolder, libraryItems) {
        val groups = mutableListOf<FolderDisplayGroup>()
        val folderUrisCovered = mutableSetOf<String>()

        for (folder in folders) {
            val videos = scannedByFolder[folder.treeUri].orEmpty()
            folderUrisCovered += folder.treeUri
            val totalSize = videos.sumOf { it.sizeBytes.coerceAtLeast(0L) }
            val latestDate = videos.maxOfOrNull { it.addedAtMs }?.coerceAtLeast(folder.addedAtMs)
                ?: folder.addedAtMs
            groups.add(
                FolderDisplayGroup(
                    id = "tree_${folder.treeUri}",
                    treeUri = folder.treeUri,
                    name = folder.name,
                    videos = videos,
                    totalSizeBytes = totalSize,
                    latestAddedAtMs = latestDate,
                )
            )
        }

        val offlineStandalone = libraryItems.filter { item ->
            val scheme = Uri.parse(item.uri).scheme
            val isLocal = scheme.equals("content", ignoreCase = true) || scheme.equals("file", ignoreCase = true)
            isLocal && (item.folderUri.isBlank() || item.folderUri !in folderUrisCovered)
        }

        offlineStandalone
            .groupBy { it.folderName.ifBlank { "Device Videos" } }
            .forEach { (folderName, videos) ->
                groups.add(
                    FolderDisplayGroup(
                        id = "group_$folderName",
                        treeUri = null,
                        name = folderName,
                        videos = videos,
                        totalSizeBytes = videos.sumOf { it.sizeBytes.coerceAtLeast(0L) },
                        latestAddedAtMs = videos.maxOfOrNull { it.addedAtMs } ?: 0L,
                    )
                )
            }

        groups
    }

    val sortedFolders = remember(rawGroups, settings.librarySort, settings.librarySortAscending) {
        sortFolders(rawGroups, settings.librarySort, settings.librarySortAscending)
    }

    val onSelectSortOption: (LibrarySortOption) -> Unit = { option ->
        showSortMenu = false
        scope.launch {
            repository.updateSettings { current ->
                if (current.librarySort == option) {
                    current.copy(librarySortAscending = !current.librarySortAscending)
                } else {
                    val defaultAsc = option == LibrarySortOption.NAME
                    current.copy(librarySort = option, librarySortAscending = defaultAsc)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SimpleHomeHeader(onOpenSettings = onOpenSettings)
                    SimpleActionRow(
                        onAddFolder = launchFolderPicker,
                        onOpenFile = launchFilePicker,
                    )
                    CollectionSortBar(
                        title = stringResource(R.string.my_videos),
                        countText = "${sortedFolders.size}",
                        sortOption = settings.librarySort,
                        ascending = settings.librarySortAscending,
                        menuExpanded = showSortMenu,
                        onToggleMenu = { showSortMenu = it },
                        onSelectSort = onSelectSortOption,
                    )
                }

                VideoFolderLazyColumn(
                    folders = sortedFolders,
                    onFolderClick = { folder -> onOpenFolder(folder.name, folder.treeUri) },
                    onRemoveFolder = { treeUri -> scope.launch { repository.removeFolder(treeUri) } },
                    onAddFolder = launchFolderPicker,
                    onOpenFile = launchFilePicker,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun VideoFolderLazyColumn(
    folders: List<FolderDisplayGroup>,
    onFolderClick: (FolderDisplayGroup) -> Unit,
    onRemoveFolder: (treeUri: String) -> Unit,
    onAddFolder: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_folders_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (folders.isNotEmpty()) {
            items(folders, key = { it.id }) { folder ->
                FolderCard(
                    folder = folder,
                    onOpen = { onFolderClick(folder) },
                    onRemove = folder.treeUri?.let { treeUri -> { onRemoveFolder(treeUri) } },
                )
            }
        } else {
            item {
                EmptyFolderCard(
                    onAddFolder = onAddFolder,
                    onOpenFile = onOpenFile,
                )
            }
        }
    }
}

@Composable
private fun SimpleHomeHeader(
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Offline Video Player",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        FilledTonalIconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("settings_button"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }
    }
}

@Composable
private fun SimpleActionRow(
    onAddFolder: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onAddFolder,
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("add_folder_button"),
        ) {
            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.open_local_video))
        }
        OutlinedButton(
            onClick = onOpenFile,
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("open_file_button"),
        ) {
            Icon(Icons.Rounded.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.open_any_format))
        }
    }
}

@Composable
private fun FolderCard(
    folder: FolderDisplayGroup,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val previewUri = folder.videos.firstOrNull()?.uri
    val countLabel = if (folder.videos.size == 1) "1 video" else "${folder.videos.size} videos"
    val sizeLabel = if (folder.totalSizeBytes > 0L) MediaInfoHelper.formatFileSize(folder.totalSizeBytes) else null
    val metaLine = listOfNotNull(countLabel, sizeLabel).joinToString(" • ")

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("folder_card_${folder.name}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (previewUri != null) {
                VideoThumbnailPreview(
                    uri = previewUri,
                    contentDescription = folder.name,
                    showPlayBadge = false,
                    overlayIcon = Icons.Rounded.Folder,
                    modifier = Modifier
                        .width(76.dp)
                        .height(52.dp),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .width(76.dp)
                        .height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = folder.name,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
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

@Composable
private fun EmptyFolderCard(
    onAddFolder: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_offline_library_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.empty_library_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.empty_library_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddFolder,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.open_local_video))
                }
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.open_any_format))
                }
            }
        }
    }
}
