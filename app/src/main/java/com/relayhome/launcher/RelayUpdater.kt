package com.relayhome.launcher

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
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
    internal const val DEFAULT_INCLUDE_PRERELEASES = false

    fun includesBetas(context: Context): Boolean = readSharedPreferencesSafely(
        context,
        preferencesName,
        DEFAULT_INCLUDE_PRERELEASES
    ) { it.getBoolean(betaKey, DEFAULT_INCLUDE_PRERELEASES) }

    fun setIncludesBetas(context: Context, enabled: Boolean) {
        writeSharedPreferencesSafely(context, preferencesName) { it.putBoolean(betaKey, enabled) }
    }
}

internal object RelayUpdater {
    private const val releasesUrl = "https://api.github.com/repos/saappleg/Relay-Home-Launcher/releases?per_page=100"
    private const val repositoryAssetPath = "/saappleg/Relay-Home-Launcher/releases/download/"
    private const val maxReleaseMetadataBytes = 8L * 1024L * 1024L
    private const val maxApkBytes = 200L * 1024L * 1024L
    private const val minimumApkBytes = 256L * 1024L
    private const val maxRedirects = 5
    private val versionPattern = Regex(
        "(?i)^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-(alpha|beta|rc)(?:[.-]([0-9]+))?)?(?:\\+[0-9A-Za-z.-]+)?$"
    )
    private val unsafeAssetNamePattern = Regex("(?i)(debug|unsigned|test|mapping|symbols)")
    private val unsafeFilenameCharacterPattern = Regex("[^A-Za-z0-9._-]")

    /** Pure seams for regression tests; production update selection still uses the same parser. */
    internal fun compareVersionsForTest(left: String, right: String): Int =
        compareValues(parsedVersion(left), parsedVersion(right))

    internal fun prereleaseFlagMatchesForTest(tag: String, prerelease: Boolean): Boolean =
        parsedVersion(tag)?.let { (it.stage < STABLE_STAGE) == prerelease } ?: false

    internal fun selectReleaseFromJsonForTest(
        body: String,
        currentVersionName: String,
        includePrereleases: Boolean
    ): RelayRelease? {
        val current = parsedVersion(currentVersionName) ?: return null
        return runCatching {
            selectLatestRelease(JSONArray(body), current, includePrereleases)
        }.getOrNull()
    }

    internal fun isRepositoryAssetUrlForTest(address: String): Boolean = isRepositoryAssetUrl(address)

    internal fun cachedReleaseTagForTest(fileName: String): String? = cachedReleaseTag(fileName)

    internal data class ApkVerificationSnapshot(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val debuggable: Boolean,
        val signerDigests: Set<String>
    )

    internal fun verifyApkDecisionForTest(
        candidate: ApkVerificationSnapshot,
        installed: ApkVerificationSnapshot,
        expectedPackageName: String,
        expectedReleaseTag: String? = null
    ): Result<Unit> = runCatching {
        verifyApkDecision(candidate, installed, expectedPackageName, expectedReleaseTag)
    }

    suspend fun check(includePrereleases: Boolean): Result<RelayRelease?> = withContext(Dispatchers.IO) {
        try {
            val connection = openTrustedConnection(releasesUrl, apiOnly = true)
            try {
                check(connection.responseCode in 200..299) { "GitHub update check failed (HTTP ${connection.responseCode})." }
                checkTrustedFinalUrl(connection, apiOnly = true)
                check(connection.contentLengthLong <= maxReleaseMetadataBytes) {
                    "GitHub returned too much release metadata."
                }
                val body = connection.inputStream.use { readAtMost(it, maxReleaseMetadataBytes) }
                    .decodeToString()
                val releases = JSONArray(body)
                val current = parsedVersion(BuildConfig.VERSION_NAME)
                    ?: error("Installed version is not a supported semantic version.")
                Result.success(selectLatestRelease(releases, current, includePrereleases))
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Update checks are optional. A network, parser, or framework failure should leave
            // the existing installed build usable and let Settings show a retryable failure.
            Result.failure(error)
        }
    }

    suspend fun download(context: Context, release: RelayRelease): Result<File> = withContext(Dispatchers.IO) {
        try {
            check(isRepositoryAssetUrl(release.apkUrl)) { "The update URL is not a trusted GitHub asset." }
            val directory = File(context.cacheDir, "updates").apply {
                check(mkdirs() || isDirectory) { "Could not prepare the update cache." }
            }
            val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "-")
            val destination = File(directory, "Relay-Home-$safeTag.apk")
            val partial = File(directory, "$safeTag.apk.part")
            partial.delete()
            try {
                val connection = openTrustedConnection(release.apkUrl)
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
                verifyDownloadedApk(context, partial, release)
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
            Result.success(destination)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
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
            val expectedReleaseTag = cachedReleaseTag(candidate.name)
                ?: error("The update APK is missing its release binding.")
            val archive = inspectApk(context, candidate)
            val packageInfoFlags = packageInfoFlags()
            val installed = context.packageManager.getPackageInfo(context.packageName, packageInfoFlags)
            verifyApkDecision(
                candidate = verificationSnapshot(archive),
                installed = verificationSnapshot(installed),
                expectedPackageName = context.packageName,
                expectedReleaseTag = expectedReleaseTag
            )

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
        val url = trustedUrl(address, apiOnly)
        return (url.openConnection() as HttpURLConnection).apply {
            // Redirects are followed manually so every hop is checked for HTTPS and host.
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Relay-Home/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun selectLatestRelease(
        releases: JSONArray,
        current: Version,
        includePrereleases: Boolean
    ): RelayRelease? = (0 until releases.length()).asSequence()
        .mapNotNull { index -> parseRelease(releases.optJSONObject(index), current, includePrereleases) }
        .maxByOrNull { it.second }
        ?.first

    private fun parseRelease(
        release: org.json.JSONObject?,
        current: Version,
        includePrereleases: Boolean
    ): Pair<RelayRelease, Version>? {
        if (release == null) return null
        val draft = release.opt("draft") as? Boolean ?: return null
        val prerelease = release.opt("prerelease") as? Boolean ?: return null
        if (draft || (!includePrereleases && prerelease)) return null

        val tag = (release.opt("tag_name") as? String)?.trim().orEmpty()
        val version = parsedVersion(tag) ?: return null
        // GitHub's flag is advisory; the semantic stage in the signed release tag is the
        // authoritative channel signal used by both Stable and Beta settings.
        if (prerelease != (version.stage < STABLE_STAGE)) return null
        if (!includePrereleases && version.stage < STABLE_STAGE) return null
        if (version <= current) return null

        val assets = release.optJSONArray("assets") ?: return null
        val candidates = (0 until assets.length()).asSequence()
            .mapNotNull { assets.optJSONObject(it) }
            .mapNotNull { asset ->
                val name = (asset.opt("name") as? String)?.trim().orEmpty()
                val url = (asset.opt("browser_download_url") as? String)?.trim().orEmpty()
                val state = (asset.opt("state") as? String)?.trim()
                val size = (asset.opt("size") as? Number)?.toLong() ?: -1L
                if (!isSafeAssetName(name) || !name.endsWith(".apk", ignoreCase = true) ||
                    state != "uploaded" || !isRepositoryAssetUrl(url) ||
                    size !in minimumApkBytes..maxApkBytes
                ) {
                    null
                } else {
                    ApkAsset(name, url, size)
                }
            }
            .filterNot { it.name.contains(unsafeAssetNamePattern) }
            .toList()
        val tagWithoutV = tag.removePrefix("v").removePrefix("V")
        val expectedName = "relay-home-${tagWithoutV.replace(unsafeFilenameCharacterPattern, "-")}.apk"
        val expectedAssets = candidates.filter { it.name.equals(expectedName, ignoreCase = true) }
        val selectedAsset = when {
            expectedAssets.size == 1 -> expectedAssets.single()
            expectedAssets.size > 1 -> return null
            candidates.size == 1 -> candidates.single()
            else -> return null
        }

        return RelayRelease(
            tag = tag,
            title = (release.opt("name") as? String)?.trim().orEmpty().ifBlank { tag },
            notes = (release.opt("body") as? String)?.trim().orEmpty(),
            apkUrl = selectedAsset.url,
            pageUrl = (release.opt("html_url") as? String)?.trim().orEmpty(),
            prerelease = prerelease
        ) to version
    }

    private fun openTrustedConnection(address: String, apiOnly: Boolean = false): HttpURLConnection =
        openTrustedConnection(address, apiOnly) { nextAddress, nextApiOnly ->
            openConnection(nextAddress, nextApiOnly)
        }

    /** Injectable connection seam keeps redirect-policy tests hermetic and network-free. */
    internal fun openTrustedConnectionForTest(
        address: String,
        apiOnly: Boolean = false,
        connectionFactory: (String, Boolean) -> HttpURLConnection
    ): HttpURLConnection = openTrustedConnection(address, apiOnly, connectionFactory)

    private fun openTrustedConnection(
        address: String,
        apiOnly: Boolean,
        connectionFactory: (String, Boolean) -> HttpURLConnection
    ): HttpURLConnection {
        var nextAddress = address
        repeat(maxRedirects + 1) { redirectCount ->
            val connection = connectionFactory(nextAddress, apiOnly)
            try {
                val responseCode = connection.responseCode
                if (!isRedirect(responseCode)) return connection
                check(redirectCount < maxRedirects) { "The update endpoint redirected too many times." }
                val location = connection.getHeaderField("Location")?.trim().orEmpty()
                check(location.isNotBlank()) { "The update endpoint returned an invalid redirect." }
                val resolved = URI(nextAddress).resolve(URI(location)).toString()
                trustedUrl(resolved, apiOnly)
                connection.disconnect()
                nextAddress = resolved
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
        error("The update endpoint could not be reached safely.")
    }

    private fun isRepositoryAssetUrl(address: String): Boolean = runCatching {
        val uri = URI(address.trim())
        val url = trustedUrl(address, apiOnly = false)
        val path = uri.path.orEmpty()
        // The API's browser_download_url must be a same-repository release URL. The
        // githubusercontent hosts are accepted only as redirect destinations, never as an
        // initial asset address where their path is not repository-scoped.
        url.host.equals("github.com", ignoreCase = true) &&
            uri.query == null &&
            uri.normalize().path == path &&
            path.startsWith(repositoryAssetPath) &&
            path.split('/').none { it == "." || it == ".." }
    }.getOrDefault(false)

    private fun isAllowedAssetHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT)
        return normalized == "github.com" || normalized == "objects.githubusercontent.com" ||
            normalized.endsWith(".githubusercontent.com")
    }

    private fun trustedUrl(address: String, apiOnly: Boolean): URL {
        val uri = URI(address.trim())
        check(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS update endpoints are allowed." }
        check(uri.userInfo == null && uri.fragment == null && uri.host != null) {
            "The update endpoint URL is malformed."
        }
        check(uri.port == -1 || uri.port == 443) { "The update endpoint uses an untrusted port." }
        val host = uri.host.lowercase(Locale.ROOT)
        check(if (apiOnly) host == "api.github.com" else isAllowedAssetHost(host)) {
            "The update endpoint is not a trusted GitHub host."
        }
        return uri.toURL()
    }

    private fun isRedirect(responseCode: Int): Boolean = responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
        responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
        responseCode == 307 || responseCode == 308

    private fun isSafeAssetName(name: String): Boolean =
        name.isNotBlank() && name.length <= 255 && name.none { it == '/' || it == '\\' || Character.isISOControl(it) }

    private fun readAtMost(input: java.io.InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyAtMost(input, output, limit)
        return output.toByteArray()
    }

    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    private fun inspectApk(context: Context, file: File): PackageInfo {
        val archive = context.packageManager.getPackageArchiveInfo(file.path, packageInfoFlags())
            ?: error("The downloaded APK metadata could not be read.")
        check((archive.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            "The downloaded APK is debuggable."
        }
        return archive
    }

    private fun verifyDownloadedApk(context: Context, file: File, release: RelayRelease) {
        val archive = inspectApk(context, file)
        // The shared decision seam below enforces package, signer, version and release-tag
        // invariants for both the download and install paths.
        val packageInfoFlags = packageInfoFlags()
        val installed = context.packageManager.getPackageInfo(context.packageName, packageInfoFlags)
        verifyApkDecision(
            candidate = verificationSnapshot(archive),
            installed = verificationSnapshot(installed),
            expectedPackageName = context.packageName,
            expectedReleaseTag = release.tag
        )

    }

    private fun verificationSnapshot(packageInfo: PackageInfo): ApkVerificationSnapshot =
        ApkVerificationSnapshot(
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName,
            versionCode = versionCode(packageInfo),
            debuggable = (packageInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            signerDigests = signerStrings(packageInfo)
        )

    private fun verifyApkDecision(
        candidate: ApkVerificationSnapshot,
        installed: ApkVerificationSnapshot,
        expectedPackageName: String,
        expectedReleaseTag: String?
    ) {
        check(!candidate.debuggable) { "The downloaded APK is debuggable." }
        if (candidate.packageName != expectedPackageName) {
            check(!(expectedPackageName.endsWith(".debug") &&
                candidate.packageName == expectedPackageName.removeSuffix(".debug"))) {
                "GitHub releases update the signed production Relay Home package " +
                    "(${candidate.packageName}). This debug build ($expectedPackageName) " +
                    "cannot install production updates. Install the production Relay Home " +
                    "build separately."
            }
            error("The downloaded APK is not a Relay Home update.")
        }
        val candidateVersionName = candidate.versionName?.trim().orEmpty()
        val candidateVersion = parsedVersion(candidateVersionName)
            ?: error("The downloaded APK has an unsupported semantic version.")
        if (expectedReleaseTag != null) {
            val releaseVersion = parsedVersion(expectedReleaseTag)
                ?: error("The update release has an unsupported semantic version.")
            check(candidateVersion == releaseVersion &&
                normalizedVersionLabel(candidateVersionName) == normalizedVersionLabel(expectedReleaseTag)
            ) {
                "The downloaded APK version does not match the GitHub release tag."
            }
        }
        check(installed.signerDigests.isNotEmpty() && installed.signerDigests == candidate.signerDigests) {
            "The downloaded APK is signed with a different Relay Home certificate."
        }
        val installedVersion = parsedVersion(installed.versionName.orEmpty())
            ?: error("The installed Relay Home version is unsupported.")
        check(candidateVersion > installedVersion) { "The downloaded APK is not a newer semantic version." }
        check(candidate.versionCode > installed.versionCode) {
            "The downloaded APK is not newer than this installation."
        }
    }

    private fun normalizedVersionLabel(value: String): String =
        value.trim().removePrefix("v").removePrefix("V")

    private fun versionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

    private fun signerStrings(packageInfo: PackageInfo): Set<String> {
        val signatures: Array<android.content.pm.Signature>?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signatures = packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            signatures = packageInfo.signatures
        }
        return signatures?.map { it.toCharsString() }?.toSet().orEmpty()
    }

    private fun cachedReleaseTag(fileName: String): String? {
        if (!fileName.startsWith("Relay-Home-") || !fileName.endsWith(".apk", ignoreCase = true)) return null
        val tag = fileName.removePrefix("Relay-Home-").dropLast(4)
        return tag.takeIf { parsedVersion(it) != null }
    }

    private fun checkTrustedFinalUrl(connection: HttpURLConnection, apiOnly: Boolean = false) {
        trustedUrl(connection.url.toString(), apiOnly)
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

    private const val STABLE_STAGE = 3

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
