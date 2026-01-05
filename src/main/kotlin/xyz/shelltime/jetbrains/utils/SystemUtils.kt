package xyz.shelltime.jetbrains.utils

import java.net.InetAddress
import java.util.UUID

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

    /**
     * Get the machine hostname
     */
    fun getMachineName(): String {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            System.getenv("COMPUTERNAME")
                ?: System.getenv("HOSTNAME")
                ?: "Unknown"
        }
    }

    /**
     * Generate a new UUID string
     */
    fun generateUUID(): String {
        return UUID.randomUUID().toString()
    }
}
