package com.relayhome.launcher

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayUpdaterTest {
    @Test
    fun versionOrdering_alphaThenBetaThenRcThenStable() {
        val versions = listOf("1.0.0-alpha.1", "1.0.0-beta.1", "1.0.0-rc.1", "1.0.0")

        versions.zipWithNext().forEach { (older, newer) ->
            assertTrue(
                "$older should sort before $newer",
                RelayUpdater.compareVersionsForTest(older, newer) < 0
            )
        }
        assertTrue(RelayUpdater.compareVersionsForTest("1.0.0-beta.2", "1.0.0-beta.10") < 0)
        assertTrue(RelayUpdater.compareVersionsForTest("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun prereleaseFlagMustAgreeWithSemanticVersionStage() {
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("v1.2.3-alpha.1", prerelease = true))
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3-beta.2", prerelease = true))
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3-rc.1", prerelease = true))
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3", prerelease = false))
        assertFalse(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3-beta.2", prerelease = false))
        assertFalse(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3", prerelease = true))
    }

    @Test
    fun updateChannel_defaultsToStableUntilExplicitlyEnabled() {
        assertFalse(RelayUpdateSettings.DEFAULT_INCLUDE_PRERELEASES)
    }

    @Test
    fun releaseJson_selectsTheNewestEligibleAssetAndIgnoresMismatchedFlags() {
        val stable = release(
            tag = "v2.0.0",
            prerelease = false,
            assets = arrayOf(
                asset("relay-home-2.0.0-debug.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/debug.apk"),
                asset("relay-home-2.0.0.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/relay-home-2.0.0.apk")
            )
        )
        val betaWithWrongFlag = release(
            tag = "v9.0.0-beta.1",
            prerelease = false,
            assets = arrayOf(asset("relay-home-9.0.0-beta.1.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v9.0.0-beta.1/relay-home-9.0.0-beta.1.apk"))
        )

        val selected = RelayUpdater.selectReleaseFromJsonForTest(
            JSONArray().put(betaWithWrongFlag).put(stable).toString(),
            currentVersionName = "1.0.0",
            includePrereleases = true
        )

        assertNotNull(selected)
        assertEquals("v2.0.0", selected?.tag)
        assertTrue(selected?.apkUrl?.endsWith("relay-home-2.0.0.apk") == true)
    }

    @Test
    fun releaseJson_stableChannelExcludesValidPrereleases() {
        val beta = release(
            tag = "v2.0.0-beta.1",
            prerelease = true,
            assets = arrayOf(asset("relay-home-2.0.0-beta.1.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0-beta.1/relay-home-2.0.0-beta.1.apk"))
        )

        assertNull(
            RelayUpdater.selectReleaseFromJsonForTest(
                JSONArray().put(beta).toString(), "1.0.0", includePrereleases = false
            )
        )
    }

    @Test
    fun releaseJson_betaChannelSelectsPublishedBeta7FromBeta6() {
        val beta7 = release(
            tag = "v0.1.0-beta.7",
            prerelease = true,
            assets = arrayOf(
                asset(
                    name = "relay-home-0.1.0-beta.7.apk",
                    url = "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v0.1.0-beta.7/relay-home-0.1.0-beta.7.apk",
                    size = 9_069_806L
                )
            )
        )

        val selected = RelayUpdater.selectReleaseFromJsonForTest(
            JSONArray().put(beta7).toString(),
            currentVersionName = "0.1.0-beta.6",
            includePrereleases = true
        )

        assertNotNull(selected)
        assertEquals("v0.1.0-beta.7", selected?.tag)
        assertEquals("relay-home-0.1.0-beta.7.apk", selected?.apkUrl?.substringAfterLast('/'))
    }

    @Test
    fun releaseJson_rejectsAmbiguousAssetSets() {
        val duplicateExpected = release(
            tag = "v2.0.0",
            prerelease = false,
            assets = arrayOf(
                asset("relay-home-2.0.0.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/one.apk"),
                asset("relay-home-2.0.0.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/two.apk")
            )
        )
        val multipleUnnamed = release(
            tag = "v3.0.0",
            prerelease = false,
            assets = arrayOf(
                asset("one.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v3.0.0/one.apk"),
                asset("two.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v3.0.0/two.apk")
            )
        )

        assertNull(select(JSONArray().put(duplicateExpected), "1.0.0"))
        assertNull(select(JSONArray().put(multipleUnnamed), "1.0.0"))
    }

    @Test
    fun releaseJson_rejectsDraftsUnsafeAssetsAndWrongSizes() {
        val draft = release(
            tag = "v4.0.0",
            prerelease = false,
            draft = true,
            assets = arrayOf(asset("relay-home-4.0.0.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v4.0.0/relay-home-4.0.0.apk"))
        )
        val invalidAssets = release(
            tag = "v5.0.0",
            prerelease = false,
            assets = arrayOf(
                asset("relay-home-5.0.0.apk", "https://raw.githubusercontent.com/saappleg/Relay-Home-Launcher/main/app.apk"),
                asset("relay-home-5.0.0.apk", "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v5.0.0/relay-home-5.0.0.apk", size = 1)
            )
        )

        assertNull(select(JSONArray().put(draft), "1.0.0"))
        assertNull(select(JSONArray().put(invalidAssets), "1.0.0"))
    }

    @Test
    fun repositoryAssetUrl_requiresExactGitHubRepositoryReleasePath() {
        assertTrue(RelayUpdater.isRepositoryAssetUrlForTest("https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/relay-home-2.0.0.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://raw.githubusercontent.com/saappleg/Relay-Home-Launcher/main/app.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://gist.githubusercontent.com/user/id/raw/app.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://github.com/other/repo/releases/download/v2.0.0/app.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://github.com/saappleg/Relay-Home-Launcher/releases/download/../main/app.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/app.apk?token=unexpected"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("http://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/app.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://user:pass@github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/app.apk"))
        assertFalse(RelayUpdater.isRepositoryAssetUrlForTest("https://github.com:8443/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/app.apk"))
    }

    @Test
    fun trustedConnection_allowsTrustedMultiHopRedirectsWithinTheBound() {
        val calls = AtomicInteger()
        val final = RelayUpdater.openTrustedConnectionForTest(
            "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/app.apk"
        ) { address, _ ->
            val call = calls.getAndIncrement()
            if (call < 5) {
                fakeConnection(address, HttpURLConnection.HTTP_MOVED_TEMP, "https://objects.githubusercontent.com/hop-$call")
            } else {
                fakeConnection(address, HttpURLConnection.HTTP_OK, null)
            }
        }

        assertEquals(6, calls.get())
        assertEquals(HttpURLConnection.HTTP_OK, final.responseCode)
        final.disconnect()
    }

    @Test
    fun trustedConnection_rejectsAChainBeyondTheRedirectBound() {
        val calls = AtomicInteger()
        val result = runCatching {
            RelayUpdater.openTrustedConnectionForTest(
                "https://github.com/saappleg/Relay-Home-Launcher/releases/download/v2.0.0/app.apk"
            ) { address, _ ->
                calls.incrementAndGet()
                fakeConnection(address, HttpURLConnection.HTTP_MOVED_TEMP, "https://objects.githubusercontent.com/next")
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("too many times"))
        assertEquals(6, calls.get())
    }

    @Test
    fun trustedConnection_rejectsUntrustedHostInRedirectChain_withoutNetworkCall() {
        val factoryCalls = AtomicInteger()
        val result = runCatching {
            RelayUpdater.openTrustedConnectionForTest(
                address = "https://api.github.com/repos/saappleg/Relay-Home-Launcher/releases",
                apiOnly = true
            ) { address, _ ->
                factoryCalls.incrementAndGet()
                fakeConnection(
                    address = address,
                    responseCode = HttpURLConnection.HTTP_MOVED_TEMP,
                    location = "https://evil.example/steal"
                )
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("trusted GitHub host"))
        assertEquals(1, factoryCalls.get())
    }

    @Test
    fun cachedApkName_preservesAndRequiresReleaseTagBinding() {
        assertEquals("v2.0.0-beta.3", RelayUpdater.cachedReleaseTagForTest("Relay-Home-v2.0.0-beta.3.apk"))
        assertEquals("2.0.0+build.7", RelayUpdater.cachedReleaseTagForTest("Relay-Home-2.0.0+build.7.apk"))
        assertNull(RelayUpdater.cachedReleaseTagForTest("download.apk"))
        assertNull(RelayUpdater.cachedReleaseTagForTest("Relay-Home-v2.0.apk"))
        assertNull(RelayUpdater.cachedReleaseTagForTest("Relay-Home-v2.0.0.apk.part"))
    }

    @Test
    fun apkDecision_acceptsOnlyAValidNewerProductionUpdateWithMatchingTag() {
        val result = verify(
            candidate = snapshot(versionName = "2.0.0", versionCode = 2),
            expectedReleaseTag = "v2.0.0"
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun apkDecision_acceptsPublishedBeta7FromProductionBeta6() {
        val beta7 = snapshot(
            packageName = "com.relayhome.launcher",
            versionName = "0.1.0-beta.7",
            versionCode = 30,
            signerDigests = setOf(BETA_PRODUCTION_CERTIFICATE)
        )
        val beta6 = snapshot(
            packageName = "com.relayhome.launcher",
            versionName = "0.1.0-beta.6",
            versionCode = 29,
            signerDigests = setOf(BETA_PRODUCTION_CERTIFICATE)
        )

        val result = RelayUpdater.verifyApkDecisionForTest(
            candidate = beta7,
            installed = beta6,
            expectedPackageName = "com.relayhome.launcher",
            expectedReleaseTag = "v0.1.0-beta.7"
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun apkDecision_explainsThatDebugBuildsCannotInstallProductionReleases() {
        val result = RelayUpdater.verifyApkDecisionForTest(
            candidate = snapshot(
                packageName = "com.relayhome.launcher",
                versionName = "0.1.0-beta.7",
                versionCode = 30,
                signerDigests = setOf(BETA_PRODUCTION_CERTIFICATE)
            ),
            installed = snapshot(
                packageName = "com.relayhome.launcher.debug",
                versionName = "0.1.0-beta.5",
                versionCode = 28,
                signerDigests = setOf("debug-certificate")
            ),
            expectedPackageName = "com.relayhome.launcher.debug",
            expectedReleaseTag = "v0.1.0-beta.7"
        )

        assertTrue(result.isFailure)
        assertEquals(
            "GitHub releases update the signed production Relay Home package " +
                "(com.relayhome.launcher). This debug build (com.relayhome.launcher.debug) " +
                "cannot install production updates. Install the production Relay Home build separately.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun apkDecision_rejectsDebugWrongPackageSignerAndEmptySigner() {
        assertFailure(snapshot(debuggable = true), "debuggable")
        assertFailure(snapshot(packageName = "com.attacker.app"), "not a Relay Home")
        assertFailure(snapshot(signerDigests = setOf("evil")), "different Relay Home certificate")
        assertFailure(snapshot(), "different Relay Home certificate", installedSigners = emptySet())
    }

    @Test
    fun apkDecision_rejectsTagMismatchAndVersionDowngrades() {
        assertFailure(snapshot(versionName = "2.0.1"), "does not match the GitHub release tag")
        assertFailure(
            snapshot(versionName = "1.9.9", versionCode = 2),
            "not a newer semantic version",
            expectedReleaseTag = "v1.9.9",
            installedVersionName = "2.0.0",
            installedVersionCode = 3
        )
        assertFailure(snapshot(versionName = "2.0.0", versionCode = 1), "not newer than this installation")
    }

    private fun verify(
        candidate: RelayUpdater.ApkVerificationSnapshot,
        expectedReleaseTag: String? = null,
        installedSigners: Set<String> = setOf("release-cert"),
        installedVersionName: String = "1.0.0",
        installedVersionCode: Long = 1
    ): Result<Unit> = RelayUpdater.verifyApkDecisionForTest(
        candidate = candidate,
        installed = snapshot(versionName = installedVersionName, versionCode = installedVersionCode, signerDigests = installedSigners),
        expectedPackageName = "com.relayhome.launcher",
        expectedReleaseTag = expectedReleaseTag
    )

    private fun assertFailure(
        candidate: RelayUpdater.ApkVerificationSnapshot,
        message: String,
        installedSigners: Set<String> = setOf("release-cert"),
        expectedReleaseTag: String = "v2.0.0",
        installedVersionName: String = "1.0.0",
        installedVersionCode: Long = 1
    ) {
        val result = verify(
            candidate,
            expectedReleaseTag = expectedReleaseTag,
            installedSigners = installedSigners,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode
        )
        assertTrue("Expected failure for $candidate", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains(message))
    }

    private fun snapshot(
        packageName: String = "com.relayhome.launcher",
        versionName: String? = "2.0.0",
        versionCode: Long = 2,
        debuggable: Boolean = false,
        signerDigests: Set<String> = setOf("release-cert")
    ) = RelayUpdater.ApkVerificationSnapshot(packageName, versionName, versionCode, debuggable, signerDigests)

    private companion object {
        const val BETA_PRODUCTION_CERTIFICATE =
            "4da8c2767f2e47a8a95c74bda80e9349c4e5b1b0e8fdb2b52d3bd0775d68bc21"
    }

    private fun select(releases: JSONArray, current: String) =
        RelayUpdater.selectReleaseFromJsonForTest(releases.toString(), current, includePrereleases = true)

    private fun release(
        tag: String,
        prerelease: Boolean,
        assets: Array<JSONObject>,
        draft: Boolean = false
    ): JSONObject = JSONObject()
        .put("tag_name", tag)
        .put("name", tag)
        .put("body", "test release")
        .put("html_url", "https://github.com/saappleg/Relay-Home-Launcher/releases/tag/$tag")
        .put("draft", draft)
        .put("prerelease", prerelease)
        .put("assets", JSONArray(assets))

    private fun asset(name: String, url: String, size: Long = 300_000L): JSONObject = JSONObject()
        .put("name", name)
        .put("browser_download_url", url)
        .put("state", "uploaded")
        .put("size", size)

    private fun fakeConnection(address: String, responseCode: Int, location: String?): HttpURLConnection =
        object : HttpURLConnection(URL(address)) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = responseCode
            override fun getHeaderField(name: String?): String? =
                if (name.equals("Location", ignoreCase = true)) location else null
        }
}
