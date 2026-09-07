import java.util.concurrent.TimeUnit

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

/**
 * The profile plugin uses every connected target. Never let a local profile run install the
 * benchmark/non-minified package on a physical TV: profile collection is intended for the
 * reproducible emulator target and the test harness may uninstall packages during teardown.
 */
fun assertEmulatorOnlyTargets() {
    val adb = ProcessBuilder("adb", "devices", "-l")
        .redirectErrorStream(true)
        .start()
    check(adb.waitFor(10, TimeUnit.SECONDS)) {
        adb.destroyForcibly()
        "Could not verify ADB targets before baseline-profile collection."
    }
    check(adb.exitValue() == 0) {
        "Could not verify ADB targets before baseline-profile collection (adb exit ${adb.exitValue()})."
    }
    val output = adb.inputStream.bufferedReader().use { it.readText() }
    val physicalTargets = output.lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("List of devices attached") }
        .filter { it.substringAfterLast(' ', "").startsWith("device") || it.contains(" device ") }
        .filterNot { it.substringBefore(' ').startsWith("emulator-") }
        .toList()
    check(physicalTargets.isEmpty()) {
        "Refusing baseline-profile collection while a physical ADB target is attached: " +
            physicalTargets.joinToString { it.substringBefore(' ') } +
            ". Disconnect physical targets and run this task on the emulator."
    }
}

tasks.configureEach {
    if (name.startsWith("connected") || name.contains("BaselineProfile")) {
        doFirst { assertEmulatorOnlyTargets() }
    }
}

tasks.register("generateRelayBaselineProfile") {
    group = "verification"
    description = "Collects Relay Home's startup and Home-navigation baseline profile on a connected emulator."
    dependsOn("collectNonMinifiedReleaseBaselineProfile")
}
