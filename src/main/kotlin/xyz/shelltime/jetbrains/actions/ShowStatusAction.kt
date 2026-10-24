package xyz.shelltime.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
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

        // The socket round trip must not block the UI thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val status = runBlocking { socketClient.getStatus() }

            val message = if (status != null && socketClient.isConnected()) {
                val uptime = status.uptime ?: "Unknown"
                ShellTimeBundle.message(
                    "action.showStatus.connected",
                    status.version ?: "Unknown",
                    uptime,
                    status.platform ?: "Unknown"
                )
            } else {
                ShellTimeBundle.message("action.showStatus.disconnected")
            }

            if (!project.isDisposed) {
                showNotification(
                    project,
                    ShellTimeBundle.message("action.showStatus.title"),
                    message,
                    if (socketClient.isConnected()) NotificationType.INFORMATION else NotificationType.WARNING
                )
            }
        }
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
