package xyz.shelltime.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import xyz.shelltime.jetbrains.heartbeat.HeartbeatCollector
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSender
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSenderCallback
import xyz.shelltime.jetbrains.version.VersionChecker
import kotlinx.coroutines.*

/**
 * Project-level service for ShellTime
 * Manages heartbeat collection and sending for each project
 */
@Service(Service.Level.PROJECT)
class ShellTimeProjectService(private val project: Project) : Disposable {

    private val appService = ShellTimeService.getInstance()
    private val settings get() = appService.getSettings()

    private lateinit var collector: HeartbeatCollector
    private lateinit var sender: HeartbeatSender
    private var versionChecker: VersionChecker? = null
    private var statusCallback: HeartbeatSenderCallback? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val editorFactoryListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            val editor = event.editor
            val editorProject = editor.project

            // Only register listeners for editors in this project
            if (editorProject == project) {
                collector.registerEditor(editor)
            }
        }
    }

    init {
        if (settings.enabled) {
            start()
        }
    }

    /**
     * Start tracking for this project
     */
    private fun start() {
        val debug = settings.debug

        collector = HeartbeatCollector(
            project = project,
            pluginVersion = appService.getPluginVersion(),
            debug = debug
        )

        sender = HeartbeatSender(
            collector = collector,
            socketClient = appService.getSocketClient(),
            flushInterval = settings.heartbeatInterval,
            debug = debug
        )

        // Set status callback if already registered
        statusCallback?.let { sender.setCallback(it) }

        // Register for editor events
        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, this)

        // Start the sender
        sender.start()

        // Check CLI version in background (non-blocking)
        checkCliVersion()
    }

    /**
     * Check CLI version against server
     */
    private fun checkCliVersion() {
        val apiEndpoint = settings.apiEndpoint
        val webEndpoint = settings.webEndpoint

        if (apiEndpoint.isNullOrEmpty() || webEndpoint.isNullOrEmpty()) {
            return
        }

        versionChecker = VersionChecker(apiEndpoint, webEndpoint, settings.debug)

        scope.launch {
            try {
                val socketClient = appService.getSocketClient()
                val status = socketClient.getStatus()
                val daemonVersion = status?.version

                if (daemonVersion != null) {
                    versionChecker?.checkVersion(daemonVersion, project)
                }
            } catch (e: Exception) {
                // Silently ignore - version check is optional
            }
        }
    }

    /**
     * Set status callback (called by StatusBarWidget)
     */
    fun setStatusCallback(callback: HeartbeatSenderCallback) {
        statusCallback = callback
        if (::sender.isInitialized) {
            sender.setCallback(callback)
        }
    }

    /**
     * Handle file switch event
     */
    fun onFileSwitch(file: VirtualFile?) {
        if (!settings.enabled || !::collector.isInitialized) return
        collector.handleFileSwitch(file)
    }

    /**
     * Handle file save event
     */
    fun onFileSave(file: VirtualFile?) {
        if (!settings.enabled || !::collector.isInitialized) return
        collector.handleFileSave(file)
    }

    /**
     * Force flush heartbeats
     *
     * @return Number of heartbeats flushed
     */
    fun forceFlush(): Int {
        if (!::sender.isInitialized) return 0
        return sender.forceFlush()
    }

    /**
     * Get pending heartbeat count
     */
    fun getPendingCount(): Int {
        if (!::sender.isInitialized) return 0
        return sender.getPendingCount()
    }

    /**
     * Update settings (called when preferences change)
     */
    fun updateSettings() {
        if (!settings.enabled) {
            // Disable tracking
            if (::sender.isInitialized) {
                sender.stop()
            }
            return
        }

        if (::collector.isInitialized) {
            collector.setDebug(settings.debug)
        }

        if (::sender.isInitialized) {
            sender.setDebug(settings.debug)
            sender.setFlushInterval(settings.heartbeatInterval)
        }
    }

    override fun dispose() {
        if (::sender.isInitialized) {
            sender.dispose()
        }
        if (::collector.isInitialized) {
            collector.dispose()
        }
        versionChecker?.dispose()
        scope.cancel()
    }
}
