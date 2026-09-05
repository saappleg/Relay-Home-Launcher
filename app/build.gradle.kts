import java.util.Properties

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
val relayVersionCode = providers.environmentVariable("RELAY_VERSION_CODE").orNull?.toIntOrNull() ?: 12
val relayVersionName = providers.environmentVariable("RELAY_VERSION_NAME").orNull?.takeIf { it.isNotBlank() }
    ?: "0.1.0-alpha.6"

// Never accidentally publish an unsigned (or debug-signed) release APK.
tasks.configureEach {
    if (name.contains("Release", ignoreCase = true)) {
        doFirst {
            check(releaseSigningConfigured) {
                "Release signing is not configured. Add relay.signing.* values to local.properties or RELAY_SIGNING_* environment variables."
            }
            check(rootProject.file(releaseStoreFile).isFile) {
                "Release keystore was not found at: $releaseStoreFile"
            }
        }
    }
}

android {
    namespace = "com.relayhome.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.relayhome.launcher"
        minSdk = 26
        targetSdk = 34
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
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
