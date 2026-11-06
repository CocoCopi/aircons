package app.aircons.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.aircons.BuildConfig
import app.aircons.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object UpdateManager {
    const val RELEASES_URL = "https://api.github.com/repos/CocoCopi/aircons/releases"
    private const val LATEST_URL = "$RELEASES_URL/latest"

    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val text = getText(LATEST_URL)
            val root = JSONObject(text)
            if (root.has("message")) return@runCatching null
            val assets = root.optJSONArray("assets") ?: return@runCatching null
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", null)
                    size = asset.optLong("size", 0L)
                    break
                }
            }
            val url = apkUrl ?: return@runCatching null
            val publishedAt = runCatching {
                Instant.parse(root.optString("published_at", "")).toEpochMilli()
            }.getOrDefault(0L)
            UpdateInfo(
                tag = root.optString("tag_name", "latest"),
                name = root.optString("name", "AirCons latest"),
                notes = root.optString("body", ""),
                apkUrl = url,
                sizeBytes = size,
                publishedAt = publishedAt
            )
        }
    }

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "aircons-${safeName(info.tag)}.apk")
            val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "AirCons-Android")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Download failed: HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                onProgress(100)
                target
            } finally {
                connection.disconnect()
            }
        }
    }

    fun canInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun install(context: Context, file: File): Boolean {
        if (!canInstall(context)) {
            requestInstallPermission(context)
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun isNewer(info: UpdateInfo): Boolean {
        val current = BuildConfig.VERSION_NAME.substringBefore("-")
        val release = versionNumbers(info.name) ?: versionNumbers(info.tag) ?: return false
        return compareVersions(release, current) > 0
    }

    private fun versionNumbers(text: String): String? =
        Regex("""(\d+)\.(\d+)\.(\d+)""").find(text)?.value

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split(".").mapNotNull { it.toIntOrNull() }
        val right = b.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val diff = (left.getOrElse(i) { 0 }) - (right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "AirCons-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
