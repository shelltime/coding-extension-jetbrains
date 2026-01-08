package xyz.shelltime.jetbrains.config

import com.moandjiezana.toml.Toml
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Loads and caches configuration from ~/.shelltime/config.toml or YAML variants
 */
class ConfigLoader {
    private var cachedConfig: ShellTimeConfig? = null
    private var cachedMtime: Long = 0
    private var cachedPath: String? = null

    private val configPaths = listOf(
        "~/.shelltime/config.toml",
        "~/.shelltime/config.yaml",
        "~/.shelltime/config.yml"
    )

    /**
     * Get the configuration, loading from file if needed
     */
    fun getConfig(): ShellTimeConfig {
        val configFile = findConfigFile()

        if (configFile != null) {
            val mtime = getFileMtime(configFile)
            val path = configFile.absolutePath

            // Return cached config if file hasn't changed
            if (cachedConfig != null && cachedPath == path && cachedMtime == mtime) {
                return cachedConfig!!
            }

            // Load and cache new config
            val fileConfig = loadConfigFile(configFile)
            cachedConfig = mergeConfig(fileConfig)
            cachedMtime = mtime
            cachedPath = path
            return cachedConfig!!
        }

        // Return default config if no file found
        if (cachedConfig == null) {
            cachedConfig = ShellTimeConfig()
        }
        return cachedConfig!!
    }

    /**
     * Force reload configuration from file
     */
    fun reloadConfig(): ShellTimeConfig {
        cachedConfig = null
        cachedMtime = 0
        cachedPath = null
        return getConfig()
    }

    private fun findConfigFile(): File? {
        for (path in configPaths) {
            val expandedPath = expandPath(path)
            val file = File(expandedPath)
            if (file.exists() && file.isFile) {
                return file
            }
        }
        return null
    }

    private fun expandPath(path: String): String {
        return if (path.startsWith("~")) {
            System.getProperty("user.home") + path.substring(1)
        } else {
            path
        }
    }

    private fun getFileMtime(file: File): Long {
        return try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            attrs.lastModifiedTime().toMillis()
        } catch (e: Exception) {
            0L
        }
    }

    private fun loadConfigFile(file: File): ShellTimeFileConfig {
        return try {
            when {
                file.name.endsWith(".toml") -> loadTomlConfig(file)
                file.name.endsWith(".yaml") || file.name.endsWith(".yml") -> loadYamlConfig(file)
                else -> ShellTimeFileConfig()
            }
        } catch (e: Exception) {
            // Log error but return default config
            ShellTimeFileConfig()
        }
    }

    private fun loadTomlConfig(file: File): ShellTimeFileConfig {
        val toml = Toml().read(file)

        val codeTracking = toml.getTable("codeTracking")?.let { ct ->
            CodeTrackingConfig(
                enabled = ct.getBoolean("enabled") ?: true,
                apiEndpoint = ct.getString("apiEndpoint"),
                token = ct.getString("token")
            )
        }

        @Suppress("UNCHECKED_CAST")
        return ShellTimeFileConfig(
            socketPath = toml.getString("socketPath"),
            token = toml.getString("token"),
            apiEndpoint = toml.getString("apiEndpoint"),
            webEndpoint = toml.getString("webEndpoint"),
            codeTracking = codeTracking,
            flushCount = toml.getLong("flushCount")?.toInt(),
            gcTime = toml.getLong("gcTime")?.toInt(),
            dataMasking = toml.getBoolean("dataMasking"),
            enableMetrics = toml.getBoolean("enableMetrics"),
            encrypted = toml.getBoolean("encrypted"),
            exclude = toml.getList<String>("exclude")
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYamlConfig(file: File): ShellTimeFileConfig {
        val yaml = Yaml()
        val data: Map<String, Any?> = yaml.load(file.inputStream()) ?: return ShellTimeFileConfig()

        val codeTracking = (data["codeTracking"] as? Map<String, Any?>)?.let { ct ->
            CodeTrackingConfig(
                enabled = ct["enabled"] as? Boolean ?: true,
                apiEndpoint = ct["apiEndpoint"] as? String,
                token = ct["token"] as? String
            )
        }

        return ShellTimeFileConfig(
            socketPath = data["socketPath"] as? String,
            token = data["token"] as? String,
            apiEndpoint = data["apiEndpoint"] as? String,
            webEndpoint = data["webEndpoint"] as? String,
            codeTracking = codeTracking,
            flushCount = (data["flushCount"] as? Number)?.toInt(),
            gcTime = (data["gcTime"] as? Number)?.toInt(),
            dataMasking = data["dataMasking"] as? Boolean,
            enableMetrics = data["enableMetrics"] as? Boolean,
            encrypted = data["encrypted"] as? Boolean,
            exclude = data["exclude"] as? List<String>
        )
    }

    private fun mergeConfig(fileConfig: ShellTimeFileConfig): ShellTimeConfig {
        // Determine if enabled from codeTracking section or default
        val enabled = fileConfig.codeTracking?.enabled ?: true

        return ShellTimeConfig(
            enabled = enabled,
            socketPath = fileConfig.socketPath ?: Constants.DEFAULT_SOCKET_PATH,
            heartbeatInterval = Constants.DEFAULT_FLUSH_INTERVAL_MS,
            debug = false,
            exclude = fileConfig.exclude ?: emptyList(),
            apiEndpoint = fileConfig.apiEndpoint,
            webEndpoint = fileConfig.webEndpoint
        )
    }

    /**
     * Get raw file config for accessing apiEndpoint/webEndpoint
     */
    fun getFileConfig(): ShellTimeFileConfig? {
        val configFile = findConfigFile() ?: return null
        return loadConfigFile(configFile)
    }
}
