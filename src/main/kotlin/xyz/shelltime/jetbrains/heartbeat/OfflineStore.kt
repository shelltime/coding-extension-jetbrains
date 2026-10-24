package xyz.shelltime.jetbrains.heartbeat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.shelltime.jetbrains.config.Constants
import java.io.File

/**
 * The daemon's file for heartbeats it could not send (cli/model/db.go HEARTBEAT_LOG_FILE):
 * one {"heartbeats": [...]} payload per line, resent when the daemon starts and every
 * 30 minutes. Heartbeat ids make a resend of one that did arrive harmless.
 */
object OfflineStore {
    val DEFAULT_FILE = File(System.getProperty("user.home"), ".shelltime/coding-heartbeat.data.log")

    // Don't grow the file without bound when no daemon ever reads it
    private const val MAX_FILE_BYTES = 50L * 1024 * 1024

    private val json = Json { encodeDefaults = true }

    /**
     * Leave heartbeats that could not be delivered before the IDE closes for the daemon
     * to send once it runs again
     *
     * @return Whether they were written
     */
    fun save(heartbeats: List<HeartbeatData>, file: File = DEFAULT_FILE): Boolean {
        if (heartbeats.isEmpty()) return true
        return try {
            if (file.exists() && file.length() > MAX_FILE_BYTES) return false
            file.parentFile?.mkdirs()
            val lines = heartbeats.chunked(Constants.MAX_HEARTBEATS_PER_MESSAGE)
                .joinToString("") { json.encodeToString(HeartbeatPayload(heartbeats = it)) + "\n" }
            file.appendText(lines)
            true
        } catch (e: Exception) {
            false
        }
    }
}
