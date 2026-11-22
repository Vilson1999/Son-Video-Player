package com.sonvideo.player.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.sonvideo.player.data.VideoEntry
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfo
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalFileMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val folderName: String,
)

data class InspectedMediaInfo(
    val format: String,
    val durationMs: Long,
    val videoStreams: List<VideoStreamSummary>,
    val audioStreams: List<AudioStreamSummary>,
    val subtitleStreams: List<SubtitleStreamSummary>,
)

data class VideoStreamSummary(
    val index: Int,
    val codec: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val bitRate: Long,
    val title: String?,
)

data class AudioStreamSummary(
    val index: Int,
    val codec: String,
    val channels: Int,
    val sampleRate: Int,
    val language: String?,
    val title: String?,
)

data class SubtitleStreamSummary(
    val index: Int,
    val codec: String,
    val language: String?,
    val title: String?,
)

object MediaInfoHelper {

    suspend fun inspect(context: Context, uriString: String): Result<InspectedMediaInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                require(uri.scheme == "content" || uri.scheme == "file") {
                    "Only local offline media files are supported"
                }
                val info: MediaInfo = MediaInfoBuilder().from(context, uri).build()
                    ?: error("Unable to open local media container")

                val video = info.videoStream?.let { vs ->
                    listOf(
                        VideoStreamSummary(
                            index = vs.index,
                            codec = vs.codecName,
                            width = vs.frameWidth,
                            height = vs.frameHeight,
                            frameRate = vs.frameRate,
                            bitRate = vs.bitRate,
                            title = vs.title,
                        )
                    )
                }.orEmpty()

                val audio = info.audioStreams.map { a ->
                    AudioStreamSummary(
                        index = a.index,
                        codec = a.codecName,
                        channels = a.channels,
                        sampleRate = a.sampleRate,
                        language = a.language,
                        title = a.title,
                    )
                }

                val subs = info.subtitleStreams.map { s ->
                    SubtitleStreamSummary(
                        index = s.index,
                        codec = s.codecName,
                        language = s.language,
                        title = s.title,
                    )
                }

                InspectedMediaInfo(
                    format = info.format,
                    durationMs = info.duration,
                    videoStreams = video,
                    audioStreams = audio,
                    subtitleStreams = subs,
                )
            }
        }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) {
                                val name = cursor.getString(idx)
                                if (!name.isNullOrBlank()) return name
                            }
                        }
                    }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.toString()
    }

    fun resolveFileDetails(context: Context, uri: Uri): LocalFileMetadata {
        var name: String? = null
        var size = 0L
        var lastModified = System.currentTimeMillis()
        var folderName = "Device Videos"

        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) name = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    }
                }
            }
            if (DocumentsContract.isDocumentUri(context, uri)) {
                runCatching {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val pathPart = docId.substringAfter(':', "")
                    if (pathPart.contains('/')) {
                        val parent = pathPart.substringBeforeLast('/').substringAfterLast('/')
                        if (parent.isNotBlank()) folderName = parent
                    }
                    context.contentResolver.query(
                        uri,
                        arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                                val ts = cursor.getLong(modIdx)
                                if (ts > 0L) lastModified = ts
                            }
                        }
                    }
                }
            }
        }

        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.toString()

        return LocalFileMetadata(
            displayName = resolvedName,
            sizeBytes = size,
            dateAddedMs = lastModified,
            folderName = folderName,
        )
    }

    fun resolveFolderName(context: Context, treeUri: Uri): String {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (treeDocId != null) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            runCatching {
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            }
            val fallback = treeDocId.substringAfterLast(':').substringAfterLast('/')
            if (fallback.isNotBlank()) return fallback
        }
        return "Video Folder"
    }

    suspend fun scanFolderVideos(
        context: Context,
        treeUri: Uri,
        rootFolderName: String,
    ): List<VideoEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoEntry>()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext emptyList()
        val treeUriString = treeUri.toString()

        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootDocId to 0)

        while (queue.isNotEmpty() && results.size < 400) {
            val (parentDocId, depth) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )

            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext() && results.size < 400) {
                        val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                        val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: continue else continue
                        val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        val lastMod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < 2 && !displayName.startsWith(".")) {
                                queue.add(docId to depth + 1)
                            }
                        } else if (isVideoDocument(mimeType, displayName)) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            results.add(
                                VideoEntry(
                                    uri = fileUri.toString(),
                                    title = displayName,
                                    subtitle = rootFolderName,
                                    formatTag = detectFormatTag(displayName),
                                    addedAtMs = if (lastMod > 0L) lastMod else System.currentTimeMillis(),
                                    sizeBytes = sizeBytes,
                                    folderUri = treeUriString,
                                    folderName = rootFolderName,
                                )
                            )
                        }
                    }
                }
            }
        }
        results
    }

    suspend fun scanDeviceVideos(
        context: Context,
        bucketIdFilter: String? = null,
    ): List<VideoEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoEntry>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val selection = if (bucketIdFilter != null) {
            "${MediaStore.Video.Media.BUCKET_ID} = ?"
        } else {
            null
        }
        val selectionArgs = bucketIdFilter?.let { arrayOf(it) }
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durationIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dateAddedIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val dateModIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val bucketIdIdx = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameIdx = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val title = if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: "Video_$id"
                    } else {
                        "Video_$id"
                    }
                    val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val durationMs = if (durationIdx >= 0 && !cursor.isNull(durationIdx)) cursor.getLong(durationIdx) else 0L
                    val dateAddedSec = if (dateAddedIdx >= 0 && !cursor.isNull(dateAddedIdx)) cursor.getLong(dateAddedIdx) else 0L
                    val dateModSec = if (dateModIdx >= 0 && !cursor.isNull(dateModIdx)) cursor.getLong(dateModIdx) else 0L
                    val addedAtMs = when {
                        dateAddedSec > 0L -> dateAddedSec * 1000L
                        dateModSec > 0L -> dateModSec * 1000L
                        else -> 0L
                    }
                    val bucketId = if (bucketIdIdx >= 0 && !cursor.isNull(bucketIdIdx)) {
                        cursor.getString(bucketIdIdx).orEmpty()
                    } else {
                        "default"
                    }
                    val bucketName = if (bucketNameIdx >= 0) {
                        cursor.getString(bucketNameIdx)?.takeIf { it.isNotBlank() } ?: "Device Videos"
                    } else {
                        "Device Videos"
                    }

                    results.add(
                        VideoEntry(
                            uri = contentUri.toString(),
                            title = title,
                            subtitle = bucketName,
                            formatTag = detectFormatTag(title),
                            durationMs = durationMs,
                            addedAtMs = addedAtMs,
                            sizeBytes = sizeBytes,
                            folderUri = "mediastore:$bucketId",
                            folderName = bucketName,
                        )
                    )
                }
            }
        }
        results
    }

    private fun isVideoDocument(mimeType: String, fileName: String): Boolean {
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        if (mimeType.equals("application/x-matroska", ignoreCase = true)) return true
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in setOf(
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "flv", "wmv", "3gp", "ts", "m2ts", "mpg", "mpeg", "vob", "ogv",
        )
    }

    fun detectFormatTag(titleOrUri: String): String {
        val ext = titleOrUri.substringBefore('?').substringAfterLast('.', "").uppercase(Locale.US)
        return when (ext) {
            "MKV" -> "MKV"
            "MP4", "M4V" -> "MP4"
            "WEBM" -> "WEBM"
            "AVI" -> "AVI"
            "MOV" -> "MOV"
            "FLV" -> "FLV"
            "WMV" -> "WMV"
            "3GP" -> "3GP"
            "TS", "M2TS" -> "MPEG-TS"
            else -> "VIDEO"
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "--"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatShortDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
    }
}
