package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * In-App Auto-Update System for Legend_Carrom.
 * Checks GitHub Releases API, compares semantic versions,
 * downloads APK updates with progress tracking, and invokes Android Package Installer.
 */
object AppUpdater {

    private const val GITHUB_REPO_API =
        "https://api.github.com/repos/polytechai/Legend_Carrom/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val apkFileName: String
    )

    /**
     * Checks if a newer release exists on GitHub.
     * Returns UpdateInfo if an update is available, null otherwise.
     */
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_REPO_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Legend-Carrom-App-Updater")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)

            val rawTagName = json.optString("tag_name", "")
            val cleanLatestVersion = rawTagName.trimStart('v', 'V').trim()
            val currentVersion = BuildConfig.VERSION_NAME.trimStart('v', 'V').trim()
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")

            if (!isNewerVersion(cleanLatestVersion, currentVersion)) {
                return@withContext null
            }

            // Find APK asset in release
            var downloadUrl = ""
            var apkFileName = "Legend_Carrom_update.apk"
            val assets = json.optJSONArray("assets")

            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkFileName = name
                        break
                    }
                }
            }

            // Fallback if no specific asset found
            if (downloadUrl.isEmpty()) {
                downloadUrl = json.optString("html_url", "")
            }

            if (downloadUrl.isNotEmpty()) {
                UpdateInfo(
                    latestVersion = cleanLatestVersion,
                    currentVersion = currentVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    apkFileName = apkFileName
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads APK from GitHub to app's cache directory and triggers Android Package Installer.
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val destinationFile = File(context.cacheDir, fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Legend-Carrom-App-Updater")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("Download failed: HTTP ${response.code}")
                }
                return@withContext
            }

            val body = response.body ?: run {
                withContext(Dispatchers.Main) { onError("Empty download body received") }
                return@withContext
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
                triggerInstall(context, destinationFile)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Failed to download update: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    /**
     * Uses Android FileProvider to launch the system package installer.
     */
    fun triggerInstall(context: Context, apkFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            // Intent fallback or permission issue handled
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.2.0" vs "1.1.9").
     * Returns true if latest is strictly greater than current.
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
