package xyz.shelltime.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import xyz.shelltime.jetbrains.ShellTimeBundle
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.services.ShellTimeService

/**
 * Action to display ShellTime daemon status
 */
class ShowStatusAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val service = ShellTimeService.getInstance()
        val socketClient = service.getSocketClient()

        runBlocking {
            val status = socketClient.getStatus()

            val message = if (status != null && socketClient.isConnected()) {
                val uptime = formatUptime(status.uptime ?: 0)
                ShellTimeBundle.message(
                    "action.showStatus.connected",
                    status.version ?: "Unknown",
                    uptime,
                    status.platform ?: "Unknown"
                )
            } else {
                ShellTimeBundle.message("action.showStatus.disconnected")
            }

            showNotification(
                project,
                ShellTimeBundle.message("action.showStatus.title"),
                message,
                if (socketClient.isConnected()) NotificationType.INFORMATION else NotificationType.WARNING
            )
        }
    }

    private fun formatUptime(seconds: Long): String {
        if (seconds <= 0) return "Unknown"

        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${secs}s")
        }.trim()
    }

    private fun showNotification(
        project: Project,
        title: String,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}
