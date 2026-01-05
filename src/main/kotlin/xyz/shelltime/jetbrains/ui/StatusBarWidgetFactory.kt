package xyz.shelltime.jetbrains.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Factory for creating ShellTime status bar widgets
 */
class StatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ShellTimeStatusBarWidget.ID

    override fun getDisplayName(): String = "ShellTime"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return ShellTimeStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // Widget disposes itself
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
