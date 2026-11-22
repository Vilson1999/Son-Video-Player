package com.sonvideo.player.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(hasMediaReadPermission(context)) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var deviceVideos by remember { mutableStateOf<List<VideoEntry>>(emptyList()) }
    var scannedByFolder by remember { mutableStateOf<Map<String, List<VideoEntry>>>(emptyMap()) }
    var showSortMenu by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = hasMediaReadPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasMediaReadPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !hasRequestedPermission) {
            hasRequestedPermission = true
            permissionLauncher.launch(requiredStoragePermissions())
        }
    }

    LaunchedEffect(hasPermission) {
        deviceVideos = if (hasPermission) {
            MediaInfoHelper.scanDeviceVideos(context)
        } else {
            emptyList()
        }
    }

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

    val rawGroups = remember(folders, scannedByFolder, deviceVideos, libraryItems) {
        val groups = mutableListOf<FolderDisplayGroup>()
        val coveredUris = mutableSetOf<String>()
        val folderUrisCovered = mutableSetOf<String>()

        for (folder in folders) {
            val videos = scannedByFolder[folder.treeUri].orEmpty()
            folderUrisCovered += folder.treeUri
            videos.forEach { coveredUris += it.uri }
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

        deviceVideos
            .filter { it.uri !in coveredUris }
            .groupBy { it.folderUri.ifBlank { "mediastore:default" } }
            .forEach { (bucketUri, videos) ->
                videos.forEach { coveredUris += it.uri }
                val folderName = videos.firstOrNull()?.folderName?.ifBlank { "Device Videos" } ?: "Device Videos"
                groups.add(
                    FolderDisplayGroup(
                        id = bucketUri,
                        treeUri = bucketUri,
                        name = folderName,
                        videos = videos,
                        totalSizeBytes = videos.sumOf { it.sizeBytes.coerceAtLeast(0L) },
                        latestAddedAtMs = videos.maxOfOrNull { it.addedAtMs } ?: 0L,
                    )
                )
            }

        val offlineStandalone = libraryItems.filter { item ->
            val scheme = Uri.parse(item.uri).scheme
            val isLocal = scheme.equals("content", ignoreCase = true) || scheme.equals("file", ignoreCase = true)
            isLocal &&
                item.uri !in coveredUris &&
                (item.folderUri.isBlank() || item.folderUri !in folderUrisCovered)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SimpleHomeHeader(
                        sortOption = settings.librarySort,
                        ascending = settings.librarySortAscending,
                        sortMenuExpanded = showSortMenu,
                        onToggleSortMenu = { showSortMenu = it },
                        onSelectSort = onSelectSortOption,
                        onOpenSettings = onOpenSettings,
                    )
                    Text(
                        text = stringResource(R.string.my_videos),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                VideoFolderLazyColumn(
                    folders = sortedFolders,
                    hasPermission = hasPermission,
                    onFolderClick = { folder -> onOpenFolder(folder.name, folder.treeUri) },
                    onRemoveFolder = { treeUri -> scope.launch { repository.removeFolder(treeUri) } },
                    onGrantPermission = { permissionLauncher.launch(requiredStoragePermissions()) },
                    onOpenAppSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun requiredStoragePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
}

private fun hasMediaReadPermission(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
    }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO,
        ) == PackageManager.PERMISSION_GRANTED
    }
    else -> {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun VideoFolderLazyColumn(
    folders: List<FolderDisplayGroup>,
    hasPermission: Boolean,
    onFolderClick: (FolderDisplayGroup) -> Unit,
    onRemoveFolder: (treeUri: String) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
                val removableUri = folder.treeUri?.takeIf { it.startsWith("content://") }
                FolderCard(
                    folder = folder,
                    onOpen = { onFolderClick(folder) },
                    onRemove = removableUri?.let { treeUri -> { onRemoveFolder(treeUri) } },
                )
            }
        } else {
            item {
                EmptyFolderCard(
                    hasPermission = hasPermission,
                    onGrantPermission = onGrantPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
        }
    }
}

@Composable
private fun SimpleHomeHeader(
    sortOption: LibrarySortOption,
    ascending: Boolean,
    sortMenuExpanded: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    onSelectSort: (LibrarySortOption) -> Unit,
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

            SortMenuButton(
                sortOption = sortOption,
                ascending = ascending,
                menuExpanded = sortMenuExpanded,
                onToggleMenu = onToggleSortMenu,
                onSelectSort = onSelectSort,
            )
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
    val sizeLabel = if (folder.totalSizeBytes > 0L) {
        MediaInfoHelper.formatFileSize(folder.totalSizeBytes)
    } else {
        "Video folder"
    }

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
                    text = sizeLabel,
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
    hasPermission: Boolean,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
                        imageVector = if (hasPermission) Icons.Rounded.Folder else Icons.Rounded.PermMedia,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = stringResource(
                    if (hasPermission) R.string.empty_library_title else R.string.permission_required_title
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (hasPermission) R.string.empty_library_subtitle else R.string.permission_required_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!hasPermission) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onGrantPermission,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_media_permission_button"),
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_app_settings_button"),
                    ) {
                        Text(stringResource(R.string.open_app_settings))
                    }
                }
            }
        }
    }
}
