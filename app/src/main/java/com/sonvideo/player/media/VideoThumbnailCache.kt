package com.sonvideo.player.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object VideoThumbnailCache {

    private const val MAX_MEMORY_CACHE_KB = 12 * 1024 // 12 MB
    private const val THUMB_TARGET_WIDTH = 360
    private const val THUMB_TARGET_HEIGHT = 204

    private val memoryCache = object : LruCache<String, ImageBitmap>(MAX_MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4) / 1024
        }
    }

    private val failedUris = LruCache<String, Boolean>(64)
    private val concurrencyGate = Semaphore(permits = 2)

    fun getCached(uriString: String): ImageBitmap? = memoryCache.get(uriString)

    suspend fun loadThumbnail(context: Context, uriString: String): ImageBitmap? {
        memoryCache.get(uriString)?.let { return it }
        if (failedUris.get(uriString) == true) return null

        val uri = Uri.parse(uriString)
        if (!isLocalUri(uri)) {
            failedUris.put(uriString, true)
            return null
        }

        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            concurrencyGate.withPermit {
                memoryCache.get(uriString)?.let { return@withPermit it }

                val cacheFile = diskCacheFile(appContext, uriString)
                if (cacheFile.exists() && cacheFile.length() > 0L) {
                    val decoded = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (decoded != null) {
                        val imageBitmap = decoded.asImageBitmap()
                        memoryCache.put(uriString, imageBitmap)
                        return@withPermit imageBitmap
                    }
                }

                val extracted = extractWithMediaMetadataRetriever(appContext, uri)
                if (extracted != null) {
                    writeToDiskCache(cacheFile, extracted)
                    val imageBitmap = extracted.asImageBitmap()
                    memoryCache.put(uriString, imageBitmap)
                    imageBitmap
                } else {
                    failedUris.put(uriString, true)
                    null
                }
            }
        }
    }

    private fun extractWithMediaMetadataRetriever(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val targetTimeUs = when {
                durationMs > 10_000L -> (durationMs * 100L).coerceAtMost(5_000_000L)
                durationMs > 2_000L -> 1_000_000L
                else -> 0L
            }

            retriever.getScaledFrameAtTime(
                targetTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_TARGET_WIDTH,
                THUMB_TARGET_HEIGHT,
            ) ?: retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_TARGET_WIDTH,
                THUMB_TARGET_HEIGHT,
            ) ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isLocalUri(uri: Uri): Boolean {
        return uri.scheme.equals("content", ignoreCase = true) ||
            uri.scheme.equals("file", ignoreCase = true)
    }

    private fun diskCacheFile(context: Context, uriString: String): File {
        val dir = File(context.cacheDir, "video_thumbs")
        if (!dir.exists()) dir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256").digest(uriString.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(dir, "$hex.jpg")
    }

    private fun writeToDiskCache(file: File, bitmap: Bitmap) {
        runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
        }
    }
}
