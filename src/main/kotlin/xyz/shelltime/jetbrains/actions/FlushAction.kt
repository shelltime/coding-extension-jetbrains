package xyz.shelltime.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import xyz.shelltime.jetbrains.ShellTimeBundle
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.services.ShellTimeProjectService

/**
 * Action to manually flush pending heartbeats
 */
class FlushAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val projectService = project.service<ShellTimeProjectService>()
        val flushedCount = projectService.forceFlush()

        val (message, type) = when {
            flushedCount > 0 -> {
                ShellTimeBundle.message("action.flush.success", flushedCount) to NotificationType.INFORMATION
            }
            flushedCount == 0 && projectService.getPendingCount() == 0 -> {
                ShellTimeBundle.message("action.flush.empty") to NotificationType.INFORMATION
            }
            else -> {
                ShellTimeBundle.message("action.flush.failed") to NotificationType.WARNING
            }
        }

        showNotification(
            project,
            ShellTimeBundle.message("action.flush.title"),
            message,
            type
        )
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
