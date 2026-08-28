package com.relayhome.launcher

/**
 * Runs as Shizuku's shell/root identity after the user approves Relay. It deliberately accepts
 * only a package name plus enable/disable state, rather than exposing a general command runner.
 */
class RelayShizukuService : IRelayHomeShell.Stub() {
    override fun setLauncherEnabled(packageName: String, activityName: String, enabled: Boolean): String {
        require(packageName.matches(Regex("[A-Za-z0-9_.]+"))) { "Invalid launcher package." }
        require(activityName.matches(Regex("[A-Za-z0-9_.$]+"))) { "Invalid launcher activity." }
        val stockComponent = "$packageName/$activityName"
        if (enabled) {
            runCommand("/system/bin/pm", "enable", "--user", "0", packageName)
            runCommand("/system/bin/cmd", "package", "set-home-activity", "--user", "0", stockComponent)
            val resolved = resolveHome()
            check(resolved.contains(packageName)) { "Stock launcher was enabled, but Android still resolves Home to: $resolved" }
            return "Stock launcher restored and verified: $resolved"
        }

        runCommand("/system/bin/pm", "disable-user", "--user", "0", packageName)
        val setHome = runCatching {
            runCommand(
                "/system/bin/cmd", "package", "set-home-activity", "--user", "0",
                "com.relayhome.launcher/com.relayhome.launcher.MainActivity"
            )
        }
        val resolved = runCatching { resolveHome() }.getOrElse { "verification failed: ${it.message}" }
        if (setHome.isFailure || !resolved.contains("com.relayhome.launcher")) {
            runCatching { runCommand("/system/bin/pm", "enable", "--user", "0", packageName) }
            throw IllegalStateException(
                setHome.exceptionOrNull()?.message
                    ?: "Android rejected Relay as Home (resolved: $resolved). The stock launcher was restored."
            )
        }
        return "Relay Home override applied and verified: $resolved"
    }

    private fun resolveHome(): String = runCommand(
        "/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "0",
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"
    )

    private fun runCommand(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        check(process.waitFor() == 0) { output.ifBlank { "Package manager command failed: ${command.joinToString(" ")}" } }
        return output.ifBlank { "Command completed." }
    }
}
