package xyz.shelltime.jetbrains.heartbeat

import com.intellij.openapi.Disposable
import kotlinx.coroutines.*
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.socket.SocketClient
import xyz.shelltime.jetbrains.utils.Logger
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Callback interface for status updates
 */
interface HeartbeatSenderCallback {
    fun onStatusChanged(connected: Boolean, pendingCount: Int)
}

/**
 * Periodically sends collected heartbeats to the daemon
 */
class HeartbeatSender(
    private val collector: HeartbeatCollector,
    private val socketClient: SocketClient,
    private var flushInterval: Long = Constants.DEFAULT_FLUSH_INTERVAL_MS,
    debug: Boolean = false
) : Disposable {
    private val logger = Logger("Sender", debug)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var flushJob: Job? = null
    private var callback: HeartbeatSenderCallback? = null

    // Queue for heartbeats that failed to send (offline support)
    private val failedHeartbeats = ConcurrentLinkedQueue<HeartbeatData>()

    @Volatile
    private var isRunning = false

    /**
     * Set callback for status updates
     */
    fun setCallback(callback: HeartbeatSenderCallback) {
        this.callback = callback
    }

    /**
     * Start the periodic flush timer
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        flushJob = scope.launch {
            while (isActive) {
                delay(flushInterval)
                if (isActive) {
                    flush()
                }
            }
        }

        logger.log("Started heartbeat sender with interval ${flushInterval}ms")
    }

    /**
     * Stop the periodic flush timer
     */
    fun stop() {
        isRunning = false
        flushJob?.cancel()
        flushJob = null
        logger.log("Stopped heartbeat sender")
    }

    /**
     * Flush pending heartbeats to the daemon
     *
     * @return Number of heartbeats successfully sent
     */
    suspend fun flush(): Int {
        // Collect heartbeats from collector
        val newHeartbeats = collector.flush()

        // Combine with any previously failed heartbeats
        val allHeartbeats = mutableListOf<HeartbeatData>()

        // Add failed heartbeats first (older)
        while (true) {
            val hb = failedHeartbeats.poll() ?: break
            allHeartbeats.add(hb)
        }

        // Add new heartbeats
        allHeartbeats.addAll(newHeartbeats)

        if (allHeartbeats.isEmpty()) {
            logger.log("No heartbeats to flush")
            updateStatus()
            return 0
        }

        logger.log("Flushing ${allHeartbeats.size} heartbeats")

        val payload = HeartbeatPayload(heartbeats = allHeartbeats)
        val success = socketClient.sendHeartbeats(payload)

        if (success) {
            logger.log("Successfully sent ${allHeartbeats.size} heartbeats")
            updateStatus()
            return allHeartbeats.size
        } else {
            // Re-queue failed heartbeats for retry
            allHeartbeats.forEach { failedHeartbeats.add(it) }
            logger.log("Failed to send heartbeats, queued ${allHeartbeats.size} for retry")
            updateStatus()
            return 0
        }
    }

    /**
     * Force an immediate flush (used by manual flush action)
     */
    fun forceFlush(): Int {
        return runBlocking {
            flush()
        }
    }

    /**
     * Get total pending count (collector + failed)
     */
    fun getPendingCount(): Int {
        return collector.getPendingCount() + failedHeartbeats.size
    }

    /**
     * Update the status callback
     */
    private fun updateStatus() {
        callback?.onStatusChanged(
            connected = socketClient.isConnected(),
            pendingCount = getPendingCount()
        )
    }

    /**
     * Update flush interval
     */
    fun setFlushInterval(interval: Long) {
        flushInterval = interval
        if (isRunning) {
            stop()
            start()
        }
    }

    /**
     * Set debug logging
     */
    fun setDebug(enabled: Boolean) {
        logger.setDebug(enabled)
    }

    override fun dispose() {
        stop()
        scope.cancel()
        failedHeartbeats.clear()
        logger.log("Disposed heartbeat sender")
    }
}
