package xyz.shelltime.jetbrains.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import xyz.shelltime.jetbrains.config.ConfigLoader
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.config.ShellTimeConfig
import xyz.shelltime.jetbrains.socket.SocketClient

/**
 * Application-level service for ShellTime
 * Manages global configuration and socket client
 */
@Service(Service.Level.APP)
class ShellTimeService {

    private val configLoader = ConfigLoader()
    private val socketClient: SocketClient
    private var currentSettings: ShellTimeConfig

    init {
        currentSettings = configLoader.getConfig()
        socketClient = SocketClient(
            socketPath = currentSettings.socketPath,
            debug = currentSettings.debug
        )
    }

    companion object {
        @JvmStatic
        fun getInstance(): ShellTimeService {
            return ApplicationManager.getApplication().service()
        }
    }

    /**
     * Get the current settings
     */
    fun getSettings(): ShellTimeConfig {
        return currentSettings
    }

    /**
     * Get the socket client
     */
    fun getSocketClient(): SocketClient {
        return socketClient
    }

    /**
     * Get the plugin version
     */
    fun getPluginVersion(): String {
        return try {
            val pluginDescriptor = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
                com.intellij.openapi.extensions.PluginId.getId("xyz.shelltime.extension-jetbrains")
            )
            pluginDescriptor?.version ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    /**
     * Update settings from IDE preferences
     */
    fun updateSettings(
        enabled: Boolean,
        debug: Boolean,
        socketPath: String,
        heartbeatInterval: Long
    ) {
        currentSettings = ShellTimeConfig(
            enabled = enabled,
            socketPath = socketPath,
            heartbeatInterval = heartbeatInterval,
            debug = debug,
            exclude = currentSettings.exclude
        )

        // Update socket client
        socketClient.setSocketPath(socketPath)
        socketClient.setDebug(debug)
    }

    /**
     * Reload configuration from file
     */
    fun reloadConfig() {
        val fileConfig = configLoader.reloadConfig()
        currentSettings = ShellTimeConfig(
            enabled = fileConfig.enabled,
            socketPath = fileConfig.socketPath,
            heartbeatInterval = fileConfig.heartbeatInterval,
            debug = fileConfig.debug,
            exclude = fileConfig.exclude
        )

        socketClient.setSocketPath(currentSettings.socketPath)
        socketClient.setDebug(currentSettings.debug)
    }
}
