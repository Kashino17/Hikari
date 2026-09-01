package com.hikari.app.domain.update

import com.hikari.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GithubRelease(
    val tag_name: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    val browser_download_url: String = "",
)

/** Ergebnis eines Update-Checks gegen das neueste GitHub-Release. */
sealed interface UpdateCheckResult {
    data class Available(val version: String, val downloadUrl: String) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * In-App-Updater: prüft über die GitHub-API, ob ein neueres Release existiert,
 * und lädt das APK-Asset in den App-Cache herunter.
 *
 * Nutzt absichtlich einen eigenen, schlichten OkHttpClient — der App-weite
 * Client aus dem NetworkModule schreibt jeden Request per Interceptor auf die
 * Backend-URL um und wäre für GitHub-Aufrufe unbrauchbar.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val json: Json,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            // GitHub lehnt Requests ohne User-Agent ab.
            .header("User-Agent", "Hikari/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        "GitHub-Anfrage fehlgeschlagen (HTTP ${response.code})",
                    )
                }
                val body = response.body.string()
                val release = json.decodeFromString<GithubRelease>(body)
                val version = release.tag_name.removePrefix("v")
                if (version.isBlank() || !VersionCompare.isNewer(version, BuildConfig.VERSION_NAME)) {
                    return@withContext UpdateCheckResult.UpToDate
                }
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext UpdateCheckResult.Error(
                        "Release v$version enthält kein APK",
                    )
                UpdateCheckResult.Available(version, apk.browser_download_url)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Keine Verbindung zu GitHub möglich")
        }
    }

    /**
     * Lädt das APK nach [target] und meldet den Fortschritt in Prozent
     * (0–100) über [onProgress]; bei unbekannter Gesamtgröße -1.
     */
    suspend fun downloadApk(
        url: String,
        target: File,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Hikari/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure<File>(
                        Exception("Download fehlgeschlagen (HTTP ${response.code})"),
                    )
                }
                val body = response.body
                val total = body.contentLength()
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (total > 0) {
                                (downloaded * 100 / total).toInt()
                            } else {
                                -1
                            }
                            // Nur bei Prozent-Sprüngen melden — sonst flutet der
                            // Callback den Main-Thread mit State-Updates.
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
                Result.success(target)
            }
        } catch (e: Exception) {
            target.delete()
            Result.failure(Exception("Download fehlgeschlagen", e))
        }
    }

    private companion object {
        const val RELEASES_URL =
            "https://api.github.com/repos/Kashino17/Hikari/releases/latest"
    }
}
