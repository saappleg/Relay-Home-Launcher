package com.relayhome.launcher

/**
 * Runs as Shizuku's shell/root identity after the user approves Relay. It deliberately accepts
 * only a package name plus enable/disable state, rather than exposing a general command runner.
 */
class RelayShizukuService : IRelayHomeShell.Stub() {
    override fun setLauncherEnabled(packageName: String, enabled: Boolean): String {
        require(packageName.matches(Regex("[A-Za-z0-9_.]+"))) { "Invalid launcher package." }
        val command = if (enabled) {
            arrayOf("/system/bin/pm", "enable", "--user", "0", packageName)
        } else {
            arrayOf("/system/bin/pm", "disable-user", "--user", "0", packageName)
        }
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        check(process.waitFor() == 0) { output.ifBlank { "Package manager could not change the launcher." } }
        return output.ifBlank {
            if (enabled) "Stock launcher restored." else "Stock launcher disabled. Press Home to open Relay."
        }
    }
}
