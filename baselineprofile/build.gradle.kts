plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.relayhome.launcher.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
}

baselineProfile {
    // Keep generation explicit and repeatable: invoke the connected-device task only when an
    // emulator is available, rather than making ordinary app builds depend on a device.
    useConnectedDevices = true
    // The local emulator is the reproducible collection target for this project. The generated
    // profile is still a startup/navigation signal, not a claim about TV hardware timings.
    skipBenchmarksOnEmulator = false
}

tasks.register("generateRelayBaselineProfile") {
    group = "verification"
    description = "Collects Relay Home's startup and Home-navigation baseline profile on a connected emulator."
    dependsOn("collectNonMinifiedReleaseBaselineProfile")
}
