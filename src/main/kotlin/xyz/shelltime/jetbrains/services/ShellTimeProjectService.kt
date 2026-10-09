package xyz.shelltime.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import xyz.shelltime.jetbrains.heartbeat.HeartbeatCollector
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSender
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSenderCallback
import xyz.shelltime.jetbrains.utils.SystemUtils
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

    companion object {
        /** How long closing a project may wait for the last heartbeats to be sent */
        private const val FINAL_FLUSH_TIMEOUT_MS = 2_000L
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

        // Resolve the machine name once, off the EDT
        scope.launch { SystemUtils.getMachineName() }

        // Register for editor events
        collector.start(this)

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
            // Disable tracking, but still send what was collected before
            if (::collector.isInitialized) {
                collector.stop()
            }
            if (::sender.isInitialized) {
                sender.stop()
                scope.launch { sender.flush() }
            }
            return
        }

        if (!::collector.isInitialized) {
            // Tracking was disabled when the project opened
            start()
            return
        }

        collector.setDebug(settings.debug)
        collector.start(this)

        sender.setDebug(settings.debug)
        sender.setFlushInterval(settings.heartbeatInterval)
        sender.start()
    }

    override fun dispose() {
        if (::collector.isInitialized) {
            collector.stop()
        }
        if (::sender.isInitialized) {
            sender.stop()
            // Send what is still queued; this runs when the project closes and on IDE exit
            try {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(FINAL_FLUSH_TIMEOUT_MS) { sender.flush() }
                }
            } catch (e: Exception) {
                // Best effort only
            }
            sender.dispose()
        }
        if (::collector.isInitialized) {
            collector.dispose()
        }
        versionChecker?.dispose()
        scope.cancel()
    }
}
