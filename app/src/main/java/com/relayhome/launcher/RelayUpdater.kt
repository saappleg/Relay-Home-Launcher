package com.relayhome.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class RelayRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
    val prerelease: Boolean
)

internal object RelayUpdateSettings {
    private const val preferencesName = "relay_updates"
    private const val betaKey = "include_prereleases"

    fun includesBetas(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).getBoolean(betaKey, true)

    fun setIncludesBetas(context: Context, enabled: Boolean) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit().putBoolean(betaKey, enabled).apply()
    }
}

internal object RelayUpdater {
    private const val releasesUrl = "https://api.github.com/repos/saappleg/Relay-Home-Launcher/releases?per_page=20"

    suspend fun check(includePrereleases: Boolean): Result<RelayRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(releasesUrl)
            check(connection.responseCode in 200..299) { "GitHub update check failed (HTTP ${connection.responseCode})." }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            val current = parsedVersion(BuildConfig.VERSION_NAME)
            (0 until releases.length()).asSequence().mapNotNull { index ->
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft") || (!includePrereleases && release.optBoolean("prerelease"))) return@mapNotNull null
                val tag = release.optString("tag_name")
                if (parsedVersion(tag) <= current) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: JSONArray()
                val apkUrl = (0 until assets.length()).asSequence()
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { asset ->
                        asset.optString("name").endsWith(".apk", ignoreCase = true) ||
                            asset.optString("content_type") == "application/vnd.android.package-archive"
                    }?.optString("browser_download_url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RelayRelease(
                    tag = tag,
                    title = release.optString("name").ifBlank { tag },
                    notes = release.optString("body").trim(),
                    apkUrl = apkUrl,
                    pageUrl = release.optString("html_url"),
                    prerelease = release.optBoolean("prerelease")
                )
            }.maxByOrNull { parsedVersion(it.tag) }
        }
    }

    suspend fun download(context: Context, release: RelayRelease): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "-")
            val destination = File(directory, "Relay-Home-$safeTag.apk")
            val connection = openConnection(release.apkUrl)
            check(connection.responseCode in 200..299) { "APK download failed (HTTP ${connection.responseCode})." }
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            check(destination.length() > 1_000_000L) { "The downloaded APK is incomplete." }
            destination
        }
    }

    fun install(context: Context, apk: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Allow Relay to install updates, then return and choose Download & install again."
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return "Android's installer is opening."
    }

    private fun openConnection(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Relay-Home/${BuildConfig.VERSION_NAME}")
        }

    private data class Version(val major: Int, val minor: Int, val patch: Int, val stage: Int, val stageNumber: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch, Version::stage, Version::stageNumber)
    }

    private fun parsedVersion(value: String): Version {
        val match = Regex("(?i)(\\d+)\\.(\\d+)\\.(\\d+)(?:[-.]?(alpha|beta|rc)[-.]?(\\d+)?)?").find(value)
            ?: return Version(0, 0, 0, 0, 0)
        val stageName = match.groupValues[4].lowercase()
        val stage = when (stageName) { "alpha" -> 0; "beta" -> 1; "rc" -> 2; else -> 3 }
        return Version(
            match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt(),
            stage, match.groupValues[5].toIntOrNull() ?: 0
        )
    }
}
