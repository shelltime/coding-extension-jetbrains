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

    /** Shortest flush interval accepted, so a 0 from the settings can't spin the timer */
    const val MIN_FLUSH_INTERVAL_MS = 10_000L

    /** Heartbeats per socket message, so a backlog never goes out as one huge request */
    const val MAX_HEARTBEATS_PER_MESSAGE = 500

    /** Upper bound for heartbeats kept while the daemon is unreachable, oldest dropped first */
    const val MAX_PENDING_HEARTBEATS = 5_000

    /** Socket connection timeout - 5 seconds */
    const val CONNECTION_TIMEOUT_MS = 5_000L

    /** Directory of the ShellTime CLI's config */
    const val CONFIG_DIR = "~/.shelltime"

    /** Config files in the CLI's priority order (cli/model/config.go): the first that exists */
    val BASE_CONFIG_FILES = listOf("config.yaml", "config.yml", "config.toml")

    /** Local overrides merged over the base config: the first that exists */
    val LOCAL_CONFIG_FILES = listOf("config.local.yaml", "config.local.yml", "config.local.toml")

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
