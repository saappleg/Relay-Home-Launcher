import java.util.Properties
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { input -> load(input) }
}

fun configuredValue(propertyName: String, environmentName: String): String =
    localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }.orEmpty()

val releaseStoreFile = configuredValue("relay.signing.storeFile", "RELAY_SIGNING_STORE_FILE")
val releaseStorePassword = configuredValue("relay.signing.storePassword", "RELAY_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = configuredValue("relay.signing.keyAlias", "RELAY_SIGNING_KEY_ALIAS")
val releaseKeyPassword = configuredValue("relay.signing.keyPassword", "RELAY_SIGNING_KEY_PASSWORD")
val releaseStoreType = configuredValue("relay.signing.storeType", "RELAY_SIGNING_STORE_TYPE")
    .ifBlank { if (releaseStoreFile.endsWith(".p12", ignoreCase = true) || releaseStoreFile.endsWith(".pfx", ignoreCase = true)) "PKCS12" else "JKS" }
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }
val relayTmdbApiKey = configuredValue("tmdb.apiKey", "RELAY_TMDB_API_KEY")
val relayVersionCodeOverride = providers.environmentVariable("RELAY_VERSION_CODE").orNull
val relayVersionNameOverride = providers.environmentVariable("RELAY_VERSION_NAME").orNull

// The newest published release is v0.1.0-beta.4 (versionCode 27). Keep local debug/test builds
// visibly on the next unreleased identity instead of silently presenting an obsolete beta. This
// fallback is never accepted for release packaging; releases must receive explicit version inputs.
val localNextVersionCode = 28
val localNextVersionName = "0.1.0-beta.5"
val relayVersionCode = relayVersionCodeOverride?.toIntOrNull() ?: localNextVersionCode
val relayVersionName = relayVersionNameOverride?.takeIf { it.isNotBlank() } ?: localNextVersionName
val releaseVersionCodePattern = Regex("^[1-9][0-9]{0,9}$")
val releaseVersionNamePattern = Regex(
    """^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta)\.[1-9][0-9]*)?$"""
)
val releaseVersionConfigured =
    relayVersionCodeOverride?.let { rawCode ->
        releaseVersionCodePattern.matches(rawCode) &&
            rawCode.toLongOrNull()?.let { it in 1..2_100_000_000L } == true
    } == true &&
        relayVersionNameOverride?.let { releaseVersionNamePattern.matches(it) } == true

// Never accidentally package an unsigned (or debug-signed) release APK. Keep this
// guard limited to tasks that can emit the production release artifact: release
// unit tests and lint do not need signing credentials.
val releasePackagingTasks = setOf(
    "assembleRelease",
    "bundleRelease",
    "packageRelease",
    "packageReleaseBundle",
    "packageReleaseUniversalApk",
    "signReleaseBundle"
)

/**
 * The Android connected-test harness owns the target package lifecycle. Running connected tests
 * against a real TV can alter launcher state or clean up a Relay variant during test teardown.
 * CI uses an emulator; fail closed locally when any physical ADB target is attached instead of
 * allowing a destructive test install.
 */
fun assertConnectedTestsAreEmulatorOnly() {
    val adb = runCatching {
        ProcessBuilder("adb", "devices", "-l")
            .redirectErrorStream(true)
            .start()
    }.getOrElse { error ->
        error("Could not verify ADB targets before connected tests: ${error.message ?: error::class.java.simpleName}")
    }
    val output = adb.inputStream.bufferedReader().use { it.readText() }
    check(adb.waitFor(10, TimeUnit.SECONDS)) {
        adb.destroyForcibly()
        "Could not verify ADB targets before connected tests: adb did not respond in time."
    }
    check(adb.exitValue() == 0) {
        "Could not verify ADB targets before connected tests (adb exit ${adb.exitValue()})."
    }
    val physicalTargets = output.lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("List of devices attached") }
        .filter { it.substringAfterLast(' ', "").startsWith("device") || it.contains(" device ") }
        .filterNot { it.substringBefore(' ').startsWith("emulator-") }
        .toList()
    check(physicalTargets.isEmpty()) {
        "Refusing connected Android tests while a physical ADB target is attached: " +
            physicalTargets.joinToString { it.substringBefore(' ') } +
            ". The Android test harness can remove a Relay package during cleanup; " +
            "disconnect physical targets and run the tests on an emulator."
    }
}

tasks.configureEach {
    if (name in releasePackagingTasks) {
        doFirst {
            check(releaseVersionConfigured) {
                "Release version is missing or invalid. Set RELAY_VERSION_CODE and " +
                    "RELAY_VERSION_NAME explicitly to a valid SemVer/channel value; the local " +
                    "$localNextVersionName/$localNextVersionCode fallback is for non-release builds only."
            }
            check(releaseSigningConfigured) {
                "Release signing is not configured. Add relay.signing.* values to local.properties or RELAY_SIGNING_* environment variables."
            }
            check(rootProject.file(releaseStoreFile).isFile) {
                "Release keystore was not found at: $releaseStoreFile"
            }
        }
    }
    if (name == "connectedCheck" || (name.startsWith("connected") && name.endsWith("AndroidTest"))) {
        doFirst { assertConnectedTestsAreEmulatorOnly() }
    }
}

tasks.register("verifyRelayReleaseVersion") {
    group = "verification"
    description = "Fails unless an explicit valid release version is provided through the environment."
    doLast {
        check(releaseVersionConfigured) {
            "Release version is missing or invalid. Set RELAY_VERSION_CODE and " +
                "RELAY_VERSION_NAME explicitly to a valid SemVer/channel value; the local " +
                "$localNextVersionName/$localNextVersionCode fallback is for non-release builds only."
        }
        logger.lifecycle("Relay release version: $relayVersionName (code $relayVersionCode)")
    }
}

android {
    namespace = "com.relayhome.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.relayhome.launcher"
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = relayVersionCode
        versionName = relayVersionName
        buildConfigField("String", "TMDB_API_KEY", "\"$relayTmdbApiKey\"")
    }

    buildFeatures { compose = true; buildConfig = true; aidl = true }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storeType = releaseStoreType
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Keep debug/test installs isolated from the signed production launcher. This is a
            // second line of defense if a connected-test command is ever run on a real TV.
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.zxing:core:3.5.3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
