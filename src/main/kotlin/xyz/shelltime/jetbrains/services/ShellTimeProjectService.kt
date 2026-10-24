package xyz.shelltime.jetbrains.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import xyz.shelltime.jetbrains.ShellTimeBundle
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.heartbeat.DaemonState
import xyz.shelltime.jetbrains.heartbeat.FlushResult
import xyz.shelltime.jetbrains.heartbeat.HeartbeatCollector
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSender
import xyz.shelltime.jetbrains.heartbeat.HeartbeatSenderCallback
import xyz.shelltime.jetbrains.heartbeat.OfflineStore
import xyz.shelltime.jetbrains.utils.SystemUtils
import xyz.shelltime.jetbrains.version.VersionChecker
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

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

        /** The tracking-disabled notice is shown once per IDE session, not per project */
        private val disabledNoticeShown = AtomicBoolean(false)
    }

    /** Forwards sender updates to the status bar widget and explains a disabled daemon */
    private val senderCallback = object : HeartbeatSenderCallback {
        override fun onStatusChanged(state: DaemonState, pendingCount: Int) {
            if (state == DaemonState.DISABLED) {
                notifyTrackingDisabled()
            }
            statusCallback?.onStatusChanged(state, pendingCount)
        }
    }

    private fun notifyTrackingDisabled() {
        if (project.isDisposed || !disabledNoticeShown.compareAndSet(false, true)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
            .createNotification(
                ShellTimeBundle.message("action.showStatus.title"),
                ShellTimeBundle.message("notification.trackingDisabled"),
                NotificationType.WARNING
            )
            .notify(project)
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

        sender.setCallback(senderCallback)

        // Resolve the machine name once, off the EDT
        scope.launch { SystemUtils.getMachineName() }

        // Register for editor events
        collector.start(this)

        // Start the sender, and show the daemon's state before the first flush
        sender.start()
        scope.launch { sender.refreshState() }

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
            callback.onStatusChanged(sender.state, sender.getPendingCount())
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
     * @return How many heartbeats the daemon took, and its state
     */
    fun forceFlush(): FlushResult {
        if (!::sender.isInitialized) return FlushResult(0, DaemonState.DISCONNECTED)
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
            // Not delivered: the daemon sends them once it runs again
            OfflineStore.save(sender.drainPending())
            sender.dispose()
        }
        if (::collector.isInitialized) {
            collector.dispose()
        }
        versionChecker?.dispose()
        scope.cancel()
    }
}
