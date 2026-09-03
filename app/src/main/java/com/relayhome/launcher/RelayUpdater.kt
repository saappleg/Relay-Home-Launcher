package com.relayhome.launcher

import android.content.ClipData
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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

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
    private const val releasesUrl = "https://api.github.com/repos/saapplegate/Relay-Home-Launcher/releases?per_page=100"
    private const val maxApkBytes = 200L * 1024L * 1024L
    private const val minimumApkBytes = 256L * 1024L
    private val versionPattern = Regex(
        "(?i)^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-(alpha|beta|rc)(?:[.-]([0-9]+))?)?(?:\\+[0-9A-Za-z.-]+)?$"
    )

    suspend fun check(includePrereleases: Boolean): Result<RelayRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(releasesUrl, apiOnly = true)
            try {
                check(connection.responseCode in 200..299) { "GitHub update check failed (HTTP ${connection.responseCode})." }
                checkTrustedFinalUrl(connection, apiOnly = true)
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(body)
                val current = parsedVersion(BuildConfig.VERSION_NAME)
                    ?: error("Installed version is not a supported semantic version.")
                (0 until releases.length()).asSequence().mapNotNull { index ->
                    val release = releases.optJSONObject(index) ?: return@mapNotNull null
                    val draft = release.opt("draft") as? Boolean ?: return@mapNotNull null
                    val prerelease = release.opt("prerelease") as? Boolean ?: return@mapNotNull null
                    if (draft || (!includePrereleases && prerelease)) {
                        return@mapNotNull null
                    }
                    val tag = (release.opt("tag_name") as? String)?.trim().orEmpty()
                    if (tag.isBlank()) return@mapNotNull null
                    val version = parsedVersion(tag) ?: return@mapNotNull null
                    if (version <= current) return@mapNotNull null

                    val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                    val candidates = (0 until assets.length()).asSequence()
                        .mapNotNull { assets.optJSONObject(it) }
                        .mapNotNull { asset ->
                            val name = (asset.opt("name") as? String)?.trim().orEmpty()
                            val url = (asset.opt("browser_download_url") as? String)?.trim().orEmpty()
                            if (!name.endsWith(".apk", ignoreCase = true) || !isAllowedAssetUrl(url)) {
                                null
                            } else {
                                val size = (asset.opt("size") as? Number)?.toLong() ?: -1L
                                ApkAsset(name, url, size)
                            }
                        }
                        .filterNot { it.name.contains(Regex("(?i)(debug|unsigned|test|mapping|symbols)")) }
                        .toList()
                    val tagWithoutV = tag.removePrefix("v").removePrefix("V")
                    val expectedName = "relay-home-${tagWithoutV.replace(Regex("[^A-Za-z0-9._-]"), "-")}.apk"
                    val selectedAsset = candidates.firstOrNull { it.name.equals(expectedName, ignoreCase = true) }
                        ?: candidates.singleOrNull()
                        ?: return@mapNotNull null
                    if (selectedAsset.size == 0L || selectedAsset.size > maxApkBytes) return@mapNotNull null

                    RelayRelease(
                        tag = tag,
                        title = (release.opt("name") as? String)?.trim().orEmpty().ifBlank { tag },
                        notes = (release.opt("body") as? String)?.trim().orEmpty(),
                        apkUrl = selectedAsset.url,
                        pageUrl = (release.opt("html_url") as? String)?.trim().orEmpty(),
                        prerelease = prerelease
                    ) to version
                }.maxByOrNull { it.second }?.first
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun download(context: Context, release: RelayRelease): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            check(isAllowedAssetUrl(release.apkUrl)) { "The update URL is not a trusted GitHub asset." }
            val directory = File(context.cacheDir, "updates").apply {
                check(mkdirs() || isDirectory) { "Could not prepare the update cache." }
            }
            val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "-")
            val destination = File(directory, "Relay-Home-$safeTag.apk")
            val partial = File(directory, "$safeTag.apk.part")
            partial.delete()
            try {
                val connection = openConnection(release.apkUrl)
                try {
                    check(connection.responseCode in 200..299) { "APK download failed (HTTP ${connection.responseCode})." }
                    checkTrustedFinalUrl(connection)
                    val contentLength = connection.contentLengthLong
                    check(contentLength <= maxApkBytes) { "The update APK is too large." }
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output -> copyAtMost(input, output, maxApkBytes) }
                    }
                } finally {
                    connection.disconnect()
                }
                check(partial.length() in minimumApkBytes..maxApkBytes) { "The downloaded APK is incomplete." }
                check(isValidApk(partial)) { "The downloaded file is not a valid APK." }
                try {
                    Files.move(
                        partial.toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (error: Exception) {
                partial.delete()
                throw error
            }
            destination
        }
    }

    fun install(context: Context, apk: File): String {
        return runCatching {
            val cacheDirectory = File(context.cacheDir, "updates").canonicalFile
            val candidate = apk.canonicalFile
            check(candidate.path.startsWith(cacheDirectory.path + File.separator)) {
                "Updates can only be installed from Relay's update cache."
            }
            check(candidate.isFile && candidate.canRead()) { "The update APK could not be read." }
            check(candidate.length() in minimumApkBytes..maxApkBytes && isValidApk(candidate)) {
                "The downloaded file is not a valid APK."
            }
            val archive = context.packageManager.getPackageArchiveInfo(candidate.path, 0)
                ?: error("The downloaded APK metadata could not be read.")
            check(archive.packageName == context.packageName) { "The downloaded APK is not a Relay Home update." }
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            val candidateVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
            val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else installed.versionCode.toLong()
            check(candidateVersionCode > installedVersionCode) { "The downloaded APK is not newer than this installation." }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return@runCatching "Allow Relay to install updates, then return and choose Download & install again."
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", candidate)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("Relay update", uri)
            check(context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null) {
                "No Android package installer is available on this device."
            }
            context.startActivity(intent)
            "Android's installer is opening."
        }.getOrElse { error ->
            error.message ?: "Could not start the update installer."
        }
    }

    private fun openConnection(address: String, apiOnly: Boolean = false): HttpURLConnection {
        val url = URL(address)
        require(url.protocol.equals("https", ignoreCase = true)) { "Only HTTPS update endpoints are allowed." }
        require(if (apiOnly) url.host.equals("api.github.com", ignoreCase = true) else isAllowedAssetHost(url.host)) {
            "The update endpoint is not a trusted GitHub host."
        }
        return (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Relay-Home/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun isAllowedAssetUrl(address: String): Boolean = runCatching {
        val url = URL(address)
        url.protocol.equals("https", ignoreCase = true) && isAllowedAssetHost(url.host)
    }.getOrDefault(false)

    private fun isAllowedAssetHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "github.com" || normalized == "objects.githubusercontent.com" ||
            normalized.endsWith(".githubusercontent.com")
    }

    private fun checkTrustedFinalUrl(connection: HttpURLConnection, apiOnly: Boolean = false) {
        val finalUrl = connection.url
        check(finalUrl.protocol.equals("https", ignoreCase = true)) { "The update redirect is not HTTPS." }
        check(if (apiOnly) finalUrl.host.equals("api.github.com", ignoreCase = true) else isAllowedAssetHost(finalUrl.host)) {
            "The update redirect is not a trusted GitHub host."
        }
    }

    private fun copyAtMost(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "The update APK is too large." }
            output.write(buffer, 0, count)
        }
    }

    private fun isValidApk(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null
        }
    }.getOrDefault(false)

    private data class ApkAsset(val name: String, val url: String, val size: Long)

    private data class Version(val major: Int, val minor: Int, val patch: Int, val stage: Int, val stageNumber: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch, Version::stage, Version::stageNumber)
    }

    private fun parsedVersion(value: String): Version? {
        val match = versionPattern.matchEntire(value.trim()) ?: return null
        val stageName = match.groupValues[4].lowercase()
        val stage = when (stageName) { "alpha" -> 0; "beta" -> 1; "rc" -> 2; else -> 3 }
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        val stageNumber = match.groupValues[5].toIntOrNull() ?: 0
        return Version(
            major, minor, patch, stage, stageNumber
        )
    }
}
