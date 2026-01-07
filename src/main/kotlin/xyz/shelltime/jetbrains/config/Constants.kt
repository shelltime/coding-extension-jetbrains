package xyz.shelltime.jetbrains.config

/**
 * Constants for ShellTime plugin configuration
 */
object Constants {
    /** Default Unix socket path for daemon communication */
    const val DEFAULT_SOCKET_PATH = "/tmp/shelltime.sock"

    /** Debounce interval - max 1 heartbeat per file per 30 seconds */
    const val DEBOUNCE_MS = 30_000L

    /** Default heartbeat flush interval - 2 minutes */
    const val DEFAULT_FLUSH_INTERVAL_MS = 120_000L

    /** Socket connection timeout - 5 seconds */
    const val CONNECTION_TIMEOUT_MS = 5_000L

    /** Default config file path */
    const val DEFAULT_CONFIG_PATH = "~/.shelltime/config.toml"

    /** Plugin identifier */
    const val PLUGIN_ID = "shelltime"

    /** Editor identifier */
    const val EDITOR_ID = "jetbrains"

    /** Notification group ID */
    const val NOTIFICATION_GROUP_ID = "ShellTime"

    /** Version check API endpoint path */
    const val VERSION_CHECK_ENDPOINT = "/api/v1/cli/version-check"

    /** Version check timeout in milliseconds */
    const val VERSION_CHECK_TIMEOUT_MS = 5_000L
}
