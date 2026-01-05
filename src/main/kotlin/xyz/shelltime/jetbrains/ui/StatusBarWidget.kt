package xyz.shelltime.jetbrains.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import xyz.shelltime.jetbrains.ShellTimeBundle
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSenderCallback
import xyz.shelltime.jetbrains.services.ShellTimeProjectService
import java.awt.event.MouseEvent

/**
 * Status bar widget showing ShellTime connection status
 */
class ShellTimeStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.TextPresentation,
    HeartbeatSenderCallback {

    companion object {
        const val ID = "ShellTimeStatusBarWidget"
    }

    @Volatile
    private var connected = false

    @Volatile
    private var pendingCount = 0

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        return if (connected) {
            ShellTimeBundle.message("statusbar.connected")
        } else {
            ShellTimeBundle.message("statusbar.disconnected")
        }
    }

    override fun getTooltipText(): String {
        return if (connected) {
            ShellTimeBundle.message("statusbar.tooltip.connected", pendingCount)
        } else {
            ShellTimeBundle.message("statusbar.tooltip.disconnected", pendingCount)
        }
    }

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer {
            // Execute the ShowStatus action when clicked
            val action = ActionManager.getInstance().getAction("ShellTime.ShowStatus")
            if (action != null) {
                val dataContext = DataContext { dataId ->
                    when (dataId) {
                        com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name -> project
                        else -> null
                    }
                }
                action.actionPerformed(
                    com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext(
                        "ShellTimeStatusBar",
                        null,
                        dataContext
                    )
                )
            }
        }
    }

    override fun onStatusChanged(connected: Boolean, pendingCount: Int) {
        this.connected = connected
        this.pendingCount = pendingCount

        // Update the status bar on EDT
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            myStatusBar?.updateWidget(ID)
        }
    }

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)

        // Register this widget as the callback for the project service
        if (!project.isDisposed) {
            project.service<ShellTimeProjectService>().setStatusCallback(this)
        }
    }

    override fun dispose() {
        super.dispose()
    }
}
