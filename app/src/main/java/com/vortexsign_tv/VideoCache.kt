package com.vortexsign_tv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object VideoCache {

    private const val TAG = "VideoCache"
    private const val CACHE_DIR = "video_cache"

    data class DownloadProgress(
        val url: String,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val isFinished: Boolean = false,
        val isFailed: Boolean = false
    ) {
        val progressPercent: Int get() =
            if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
        val downloadedMB: Float get() = downloadedBytes / 1024f / 1024f
        val totalMB: Float get() = totalBytes / 1024f / 1024f
    }

    fun cacheFileFor(context: Context, url: String): File {
        val hash = md5(url)
        val ext = url.substringAfterLast(".").substringBefore("?").take(4).let {
            if (it.isBlank() || it.length > 4) "mp4" else it
        }
        val dir = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
        return File(dir, "$hash.$ext")
    }

    fun isCached(context: Context, url: String): Boolean {
        val f = cacheFileFor(context, url)
        return f.exists() && f.length() > 0
    }

    fun localPathOrNull(context: Context, url: String): String? {
        return if (isCached(context, url)) cacheFileFor(context, url).absolutePath else null
    }

    suspend fun downloadVideo(
        context: Context,
        url: String,
        onProgress: (DownloadProgress) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val cacheFile = cacheFileFor(context, url)
        val fileName = url.substringAfterLast("/").take(30)

        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "Cache hit: $url")
            onProgress(DownloadProgress(url, fileName, cacheFile.length(), cacheFile.length(), isFinished = true))
            return@withContext cacheFile.absolutePath
        }

        val tempFile = File(cacheFile.absolutePath + ".tmp")
        try {
            val connection = (URL(url.replace(" ", "%20")).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                requestMethod  = "GET"
                connect()
            }
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            FileOutputStream(tempFile).use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(65_536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloadedBytes += read
                        onProgress(DownloadProgress(url, fileName, downloadedBytes, totalBytes))
                    }
                }
            }
            tempFile.renameTo(cacheFile)
            Log.d(TAG, "Download selesai: ${cacheFile.absolutePath}")
            onProgress(DownloadProgress(url, fileName, downloadedBytes, totalBytes, isFinished = true))
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Download gagal [$url]: ${e.message}")
            tempFile.delete()
            onProgress(DownloadProgress(url, fileName, 0, 0, isFailed = true))
            null
        }
    }

    fun clearAll(context: Context) {
        val dir = File(context.filesDir, CACHE_DIR)
        dir.listFiles()?.forEach { it.delete() }
    }

    fun saveLastPlaylist(context: Context, urls: List<String>) {
        context.getSharedPreferences("VortexsignPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_playlist", urls.joinToString("|"))
            .apply()
    }

    fun loadLastPlaylist(context: Context): List<String> {
        val raw = context.getSharedPreferences("VortexsignPrefs", Context.MODE_PRIVATE)
            .getString("last_playlist", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
