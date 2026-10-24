package xyz.shelltime.jetbrains.config

import com.moandjiezana.toml.Toml
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Loads and caches configuration from the files the ShellTime CLI reads: the first of
 * ~/.shelltime/config.yaml, config.yml and config.toml, with the first config.local.*
 * merged over it (cli/model/config.go)
 *
 * @param configDir Directory holding the config files, ~/.shelltime by default
 */
class ConfigLoader(
    private val configDir: String = expandPath(Constants.CONFIG_DIR)
) {
    private var cachedConfig: ShellTimeConfig? = null
    private var cachedSignature: List<Any>? = null

    /**
     * Get the configuration, loading from file if needed
     */
    fun getConfig(): ShellTimeConfig {
        val baseFile = findFile(Constants.BASE_CONFIG_FILES)
        val localFile = findFile(Constants.LOCAL_CONFIG_FILES)
        val signature = listOf(
            baseFile?.absolutePath ?: "", getFileMtime(baseFile),
            localFile?.absolutePath ?: "", getFileMtime(localFile)
        )

        // Return cached config if no file has changed
        cachedConfig?.let { if (cachedSignature == signature) return it }

        val config = if (baseFile != null) {
            mergeConfig(mergeLocalConfig(loadConfigFile(baseFile), localFile?.let { loadConfigFile(it) }))
        } else {
            ShellTimeConfig()
        }
        cachedConfig = config
        cachedSignature = signature
        return config
    }

    /**
     * Force reload configuration from file
     */
    fun reloadConfig(): ShellTimeConfig {
        cachedConfig = null
        cachedSignature = null
        return getConfig()
    }

    private fun findFile(names: List<String>): File? {
        return names.map { File(configDir, it) }.firstOrNull { it.exists() && it.isFile }
    }

    private fun getFileMtime(file: File?): Long {
        if (file == null) return 0L
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
                enabled = ct.getBoolean("enabled"),
                apiEndpoint = ct.getString("apiEndpoint"),
                token = ct.getString("token")
            )
        }

        // The CLI writes the endpoints as APIEndpoint and WebEndpoint in TOML
        @Suppress("UNCHECKED_CAST")
        return ShellTimeFileConfig(
            socketPath = toml.getString("socketPath"),
            token = toml.getString("Token") ?: toml.getString("token"),
            apiEndpoint = toml.getString("APIEndpoint") ?: toml.getString("apiEndpoint"),
            webEndpoint = toml.getString("WebEndpoint") ?: toml.getString("webEndpoint"),
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
        val data: Map<String, Any?> = file.inputStream().use { yaml.load(it) } ?: return ShellTimeFileConfig()

        val codeTracking = (data["codeTracking"] as? Map<String, Any?>)?.let { ct ->
            CodeTrackingConfig(
                enabled = ct["enabled"] as? Boolean,
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

    /**
     * Local settings override base settings when set, as in the CLI's mergeConfig
     */
    private fun mergeLocalConfig(base: ShellTimeFileConfig, local: ShellTimeFileConfig?): ShellTimeFileConfig {
        if (local == null) return base
        return base.copy(
            socketPath = local.socketPath?.takeIf { it.isNotEmpty() } ?: base.socketPath,
            token = local.token?.takeIf { it.isNotEmpty() } ?: base.token,
            apiEndpoint = local.apiEndpoint?.takeIf { it.isNotEmpty() } ?: base.apiEndpoint,
            webEndpoint = local.webEndpoint?.takeIf { it.isNotEmpty() } ?: base.webEndpoint,
            codeTracking = mergeCodeTracking(base.codeTracking, local.codeTracking),
            exclude = local.exclude?.takeIf { it.isNotEmpty() } ?: base.exclude
        )
    }

    /**
     * Field by field: a local file that only sets the code tracking token keeps tracking on
     */
    private fun mergeCodeTracking(base: CodeTrackingConfig?, local: CodeTrackingConfig?): CodeTrackingConfig? {
        if (local == null) return base
        val merged = base ?: CodeTrackingConfig()
        return merged.copy(
            enabled = local.enabled ?: merged.enabled,
            apiEndpoint = local.apiEndpoint?.takeIf { it.isNotEmpty() } ?: merged.apiEndpoint,
            token = local.token?.takeIf { it.isNotEmpty() } ?: merged.token
        )
    }

    private fun mergeConfig(fileConfig: ShellTimeFileConfig): ShellTimeConfig {
        // Determine if enabled from codeTracking section or default
        val enabled = fileConfig.codeTracking?.enabled ?: true

        return ShellTimeConfig(
            enabled = enabled,
            socketPath = fileConfig.socketPath?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_SOCKET_PATH,
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
        val baseFile = findFile(Constants.BASE_CONFIG_FILES) ?: return null
        return mergeLocalConfig(
            loadConfigFile(baseFile),
            findFile(Constants.LOCAL_CONFIG_FILES)?.let { loadConfigFile(it) }
        )
    }

    companion object {
        private fun expandPath(path: String): String {
            return if (path.startsWith("~")) {
                System.getProperty("user.home") + path.substring(1)
            } else {
                path
            }
        }
    }
}
