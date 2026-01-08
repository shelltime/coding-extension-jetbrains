package xyz.shelltime.jetbrains.heartbeat

import kotlinx.serialization.Serializable

/**
 * Entity types for heartbeat tracking
 */
enum class EntityType {
    file,
    app,
    domain
}

/**
 * Activity categories for heartbeat tracking
 */
enum class Category {
    coding,
    debugging,
    browsing
}

/**
 * Heartbeat data model - must match VSCode extension schema exactly
 * for daemon compatibility
 */
@Serializable
data class HeartbeatData(
    /** Unique identifier for this heartbeat */
    val heartbeatId: String,

    /** File path or entity being tracked */
    val entity: String,

    /** Type of entity: file, app, or domain */
    val entityType: String,

    /** Activity category: coding, debugging, or browsing */
    val category: String,

    /** Unix timestamp in seconds */
    val time: Long,

    /** Project name (last 2 path components) */
    val project: String,

    /** Full path to project root */
    val projectRootPath: String,

    /** Git branch name (empty if not in git repo) */
    val branch: String,

    /** Programming language identifier */
    val language: String,

    /** Total number of lines in file */
    val lines: Int? = null,

    /** Current line number (1-indexed) */
    val lineNumber: Int? = null,

    /** Current cursor column position */
    val cursorPosition: Int? = null,

    /** Editor identifier - always "jetbrains" */
    val editor: String,

    /** Editor/IDE version string */
    val editorVersion: String,

    /** Plugin identifier - always "shelltime" */
    val plugin: String,

    /** Plugin version string */
    val pluginVersion: String,

    /** Machine hostname */
    val machine: String,

    /** Operating system name */
    val os: String,

    /** Operating system version */
    val osVersion: String,

    /** Whether this heartbeat was triggered by a save event */
    val isWrite: Boolean
)

/**
 * Payload for sending heartbeats to the daemon
 */
@Serializable
data class HeartbeatPayload(
    val heartbeats: List<HeartbeatData>
)

/**
 * Socket message wrapper
 */
@Serializable
data class SocketMessage(
    val type: String,
    val payload: HeartbeatPayload? = null
)

/**
 * Response from daemon status request
 */
@Serializable
data class StatusResponse(
    val version: String? = null,
    val uptime: Long? = null,
    val platform: String? = null,
    val goVersion: String? = null
)

/**
 * Response from version check API
 */
@Serializable
data class VersionCheckResponse(
    val isLatest: Boolean,
    val latestVersion: String,
    val version: String
)
