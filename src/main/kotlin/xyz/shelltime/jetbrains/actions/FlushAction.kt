package xyz.shelltime.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import xyz.shelltime.jetbrains.ShellTimeBundle
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.heartbeat.DaemonState
import xyz.shelltime.jetbrains.services.ShellTimeProjectService

/**
 * Action to manually flush pending heartbeats
 */
class FlushAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val projectService = project.service<ShellTimeProjectService>()

        // The socket round trip must not block the UI thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = projectService.forceFlush()
            val pending = projectService.getPendingCount()

            val (message, type) = when {
                result.state == DaemonState.DISABLED -> {
                    ShellTimeBundle.message("action.flush.disabled", pending) to NotificationType.WARNING
                }
                result.sent > 0 && pending == 0 -> {
                    ShellTimeBundle.message("action.flush.success", result.sent) to NotificationType.INFORMATION
                }
                result.sent == 0 && pending == 0 -> {
                    ShellTimeBundle.message("action.flush.empty") to NotificationType.INFORMATION
                }
                else -> {
                    ShellTimeBundle.message("action.flush.failed") to NotificationType.WARNING
                }
            }

            if (!project.isDisposed) {
                showNotification(
                    project,
                    ShellTimeBundle.message("action.flush.title"),
                    message,
                    type
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
