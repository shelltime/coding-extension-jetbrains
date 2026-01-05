package xyz.shelltime.jetbrains.config

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import xyz.shelltime.jetbrains.ShellTimeBundle
import xyz.shelltime.jetbrains.services.ShellTimeService
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings UI for ShellTime plugin under Tools > ShellTime
 */
class ShellTimeConfigurable : Configurable {
    private var mainPanel: JPanel? = null
    private var enabledCheckbox: JBCheckBox? = null
    private var debugCheckbox: JBCheckBox? = null
    private var socketPathField: JBTextField? = null
    private var heartbeatIntervalField: JBTextField? = null

    override fun getDisplayName(): String = "ShellTime"

    override fun createComponent(): JComponent {
        val settings = ShellTimeService.getInstance().getSettings()

        enabledCheckbox = JBCheckBox(
            ShellTimeBundle.message("settings.enabled"),
            settings.enabled
        )

        debugCheckbox = JBCheckBox(
            ShellTimeBundle.message("settings.debug"),
            settings.debug
        )

        socketPathField = JBTextField(settings.socketPath, 30)
        heartbeatIntervalField = JBTextField(settings.heartbeatInterval.toString(), 10)

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckbox!!)
            .addComponent(debugCheckbox!!)
            .addLabeledComponent(
                JBLabel(ShellTimeBundle.message("settings.socketPath") + ":"),
                socketPathField!!
            )
            .addLabeledComponent(
                JBLabel(ShellTimeBundle.message("settings.heartbeatInterval") + ":"),
                heartbeatIntervalField!!
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = ShellTimeService.getInstance().getSettings()
        return enabledCheckbox?.isSelected != settings.enabled ||
            debugCheckbox?.isSelected != settings.debug ||
            socketPathField?.text != settings.socketPath ||
            heartbeatIntervalField?.text != settings.heartbeatInterval.toString()
    }

    override fun apply() {
        val service = ShellTimeService.getInstance()
        service.updateSettings(
            enabled = enabledCheckbox?.isSelected ?: true,
            debug = debugCheckbox?.isSelected ?: false,
            socketPath = socketPathField?.text ?: Constants.DEFAULT_SOCKET_PATH,
            heartbeatInterval = heartbeatIntervalField?.text?.toLongOrNull()
                ?: Constants.DEFAULT_FLUSH_INTERVAL_MS
        )
    }

    override fun reset() {
        val settings = ShellTimeService.getInstance().getSettings()
        enabledCheckbox?.isSelected = settings.enabled
        debugCheckbox?.isSelected = settings.debug
        socketPathField?.text = settings.socketPath
        heartbeatIntervalField?.text = settings.heartbeatInterval.toString()
    }

    override fun disposeUIResources() {
        mainPanel = null
        enabledCheckbox = null
        debugCheckbox = null
        socketPathField = null
        heartbeatIntervalField = null
    }
}
