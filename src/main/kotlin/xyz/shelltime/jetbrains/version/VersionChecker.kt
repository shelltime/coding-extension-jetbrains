package xyz.shelltime.jetbrains.version

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.heartbeat.VersionCheckResponse
import xyz.shelltime.jetbrains.utils.Logger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Checks CLI version against the server and notifies user if update is available
 */
class VersionChecker(
    private val apiEndpoint: String,
    private val webEndpoint: String,
    debug: Boolean = false
) {
    private val logger = Logger("VersionChecker", debug)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var hasShownWarning = false

    /**
     * Check CLI version asynchronously
     */
    fun checkVersion(currentVersion: String, project: Project?) {
        if (hasShownWarning) {
            logger.log("Version warning already shown this session, skipping")
            return
        }

        scope.launch {
            try {
                val result = fetchVersionCheck(currentVersion)
                if (result != null && !result.isLatest) {
                    hasShownWarning = true
                    withContext(Dispatchers.Main) {
                        showUpdateWarning(currentVersion, result.latestVersion, project)
                    }
                } else if (result != null) {
                    logger.log("CLI version $currentVersion is up to date")
                }
            } catch (e: Exception) {
                logger.log("Version check failed: ${e.message}")
            }
        }
    }

    private fun fetchVersionCheck(version: String): VersionCheckResponse? {
        val encodedVersion = URLEncoder.encode(version, "UTF-8")
        val url = URL("$apiEndpoint${Constants.VERSION_CHECK_ENDPOINT}?version=$encodedVersion")

        logger.log("Checking version at: $url")

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = Constants.VERSION_CHECK_TIMEOUT_MS.toInt()
        connection.readTimeout = Constants.VERSION_CHECK_TIMEOUT_MS.toInt()
        connection.setRequestProperty("Accept", "application/json")

        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<VersionCheckResponse>(response)
            } else {
                logger.log("Version check HTTP error: ${connection.responseCode}")
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdateWarning(currentVersion: String, latestVersion: String, project: Project?) {
        val message = "ShellTime CLI update available: $currentVersion -> $latestVersion"
        val updateCommand = "curl -sSL $webEndpoint/i | bash"

        NotificationGroupManager.getInstance()
            .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
            .createNotification(
                "ShellTime Update Available",
                "$message<br><br>Run: <code>$updateCommand</code>",
                NotificationType.WARNING
            )
            .addAction(object : NotificationAction("Copy Update Command") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(java.awt.datatransfer.StringSelection(updateCommand), null)
                    notification.expire()

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                        .createNotification(
                            "Update command copied to clipboard",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            })
            .notify(project)
    }

    fun setDebug(enabled: Boolean) {
        logger.setDebug(enabled)
    }

    fun dispose() {
        scope.cancel()
    }
}
