package com.sonvideo.player.ui.home

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonvideo.player.R
import com.sonvideo.player.data.LibrarySortOption
import com.sonvideo.player.data.PlaybackProgress
import com.sonvideo.player.data.PlayerSettings
import com.sonvideo.player.data.SettingsRepository
import com.sonvideo.player.data.VideoEntry
import com.sonvideo.player.media.MediaInfoHelper
import com.sonvideo.player.ui.components.MediaInfoSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVideosScreen(
    folderName: String,
    treeUri: String,
    settings: PlayerSettings,
    libraryItems: List<VideoEntry>,
    playbackPositions: Map<String, PlaybackProgress>,
    repository: SettingsRepository,
    onBack: () -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember(treeUri) { mutableStateOf(treeUri.isNotBlank()) }
    var scannedVideos by remember(treeUri) { mutableStateOf<List<VideoEntry>>(emptyList()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var inspectedItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(treeUri, folderName) {
        if (treeUri.isNotBlank()) {
            isLoading = true
            scannedVideos = MediaInfoHelper.scanFolderVideos(
                context = context,
                treeUri = Uri.parse(treeUri),
                rootFolderName = folderName,
            )
            isLoading = false
        } else {
            isLoading = false
        }
    }

    val rawVideos = remember(treeUri, folderName, scannedVideos, libraryItems) {
        if (treeUri.isNotBlank()) {
            scannedVideos
        } else {
            libraryItems.filter { item ->
                val scheme = Uri.parse(item.uri).scheme
                val isLocal = scheme.equals("content", ignoreCase = true) ||
                    scheme.equals("file", ignoreCase = true)
                isLocal && item.folderName.ifBlank { "Device Videos" } == folderName
            }
        }
    }

    val sortedVideos = remember(rawVideos, settings.librarySort, settings.librarySortAscending) {
        sortVideos(rawVideos, settings.librarySort, settings.librarySortAscending)
    }
    val totalSizeBytes = remember(sortedVideos) {
        sortedVideos.sumOf { it.sizeBytes.coerceAtLeast(0L) }
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = folderName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Offline Folder",
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
                            .testTag("folder_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("folder_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
            ) {
                CollectionSortBar(
                    title = if (sortedVideos.size == 1) "1 video" else "${sortedVideos.size} videos",
                    countText = MediaInfoHelper.formatFileSize(totalSizeBytes),
                    sortOption = settings.librarySort,
                    ascending = settings.librarySortAscending,
                    menuExpanded = showSortMenu,
                    onToggleMenu = { showSortMenu = it },
                    onSelectSort = onSelectSortOption,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "Scanning folder videos...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (sortedVideos.isEmpty()) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = "No video files found in this folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("folder_videos_lazy_column"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedVideos, key = { "vid_${it.uri}" }) { item ->
                            VideoEntryCard(
                                entry = item,
                                progress = playbackPositions[item.uri],
                                onPlay = { onPlayVideo(item.uri, item.title) },
                                onInspect = { inspectedItem = item.title to item.uri },
                                onRemove = if (item.folderUri.isBlank()) {
                                    { scope.launch { repository.removeLibraryItem(item.uri) } }
                                } else null,
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    inspectedItem?.let { (title, uri) ->
        MediaInfoSheet(
            title = title,
            uriString = uri,
            onDismiss = { inspectedItem = null },
        )
    }
}
