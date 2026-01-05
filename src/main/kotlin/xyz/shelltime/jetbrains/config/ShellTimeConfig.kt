package xyz.shelltime.jetbrains.config

/**
 * Code tracking specific configuration
 */
data class CodeTrackingConfig(
    val enabled: Boolean = true,
    val apiEndpoint: String? = null,
    val token: String? = null
)

/**
 * Configuration loaded from ~/.shelltime/config.toml or YAML
 */
data class ShellTimeFileConfig(
    val socketPath: String? = null,
    val token: String? = null,
    val apiEndpoint: String? = null,
    val webEndpoint: String? = null,
    val codeTracking: CodeTrackingConfig? = null,
    val flushCount: Int? = null,
    val gcTime: Int? = null,
    val dataMasking: Boolean? = null,
    val enableMetrics: Boolean? = null,
    val encrypted: Boolean? = null,
    val exclude: List<String>? = null
)

/**
 * Merged configuration from file and IDE settings
 */
data class ShellTimeConfig(
    /** Whether tracking is enabled */
    val enabled: Boolean = true,

    /** Path to Unix socket for daemon communication */
    val socketPath: String = Constants.DEFAULT_SOCKET_PATH,

    /** Heartbeat flush interval in milliseconds */
    val heartbeatInterval: Long = Constants.DEFAULT_FLUSH_INTERVAL_MS,

    /** Enable debug logging */
    val debug: Boolean = false,

    /** Patterns to exclude from tracking */
    val exclude: List<String> = emptyList()
)
