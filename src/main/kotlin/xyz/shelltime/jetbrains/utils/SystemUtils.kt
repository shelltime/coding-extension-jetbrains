package xyz.shelltime.jetbrains.utils

import java.io.File
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * System-related utility functions
 */
object SystemUtils {
    /**
     * Get the operating system name (macOS, Windows, Linux)
     */
    fun getOSName(): String {
        val osName = System.getProperty("os.name")?.lowercase() ?: return "Unknown"
        return when {
            osName.contains("mac") -> "macOS"
            osName.contains("win") -> "Windows"
            osName.contains("linux") -> "Linux"
            osName.contains("nix") || osName.contains("nux") -> "Linux"
            else -> osName
        }
    }

    /**
     * Get the operating system version
     */
    fun getOSVersion(): String {
        return System.getProperty("os.version") ?: "Unknown"
    }

    private val cachedMachineName: String by lazy { resolveMachineName() }

    /**
     * Get the machine hostname
     *
     * This must match the name the ShellTime CLI and the other editor plugins report
     * (plain gethostname()), because the server groups activity into sessions per
     * machine. InetAddress.getLocalHost().hostName is avoided: the JDK resolves the
     * name through DNS, which can return a different name (e.g. "host.lan" or an
     * FQDN) and can block. The result is computed once.
     */
    fun getMachineName(): String = cachedMachineName

    private fun resolveMachineName(): String {
        return readLinuxHostname()
            ?: runHostnameCommand()
            ?: System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
            ?: try {
                InetAddress.getLocalHost().hostName
            } catch (e: Exception) {
                null
            }
            ?: "Unknown"
    }

    private fun readLinuxHostname(): String? {
        return try {
            File("/proc/sys/kernel/hostname").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun runHostnameCommand(): String? {
        return try {
            val process = ProcessBuilder("hostname").redirectErrorStream(true).start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate a new UUID string
     */
    fun generateUUID(): String {
        return UUID.randomUUID().toString()
    }
}
