package xyz.shelltime.jetbrains.heartbeat

import com.intellij.openapi.Disposable
import kotlinx.coroutines.*
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.socket.HeartbeatDelivery
import xyz.shelltime.jetbrains.socket.HeartbeatTransport
import xyz.shelltime.jetbrains.utils.Logger
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * State of the daemon as the last flush found it
 */
enum class DaemonState {
    CONNECTED,
    DISCONNECTED,

    /** The daemon runs but drops editor heartbeats: code tracking was off when it started */
    DISABLED
}

/**
 * Outcome of one flush
 */
data class FlushResult(val sent: Int, val state: DaemonState)

/**
 * Where the sender takes newly collected heartbeats from
 */
interface HeartbeatSource {
    /** Take every collected heartbeat, oldest first, leaving none queued */
    fun flush(): List<HeartbeatData>

    fun getPendingCount(): Int
}

/**
 * Callback interface for status updates
 */
interface HeartbeatSenderCallback {
    fun onStatusChanged(state: DaemonState, pendingCount: Int)
}

/**
 * Periodically sends collected heartbeats to the daemon
 */
class HeartbeatSender(
    private val collector: HeartbeatSource,
    private val socketClient: HeartbeatTransport,
    flushInterval: Long = Constants.DEFAULT_FLUSH_INTERVAL_MS,
    debug: Boolean = false,
    private val offlineFile: File = OfflineStore.DEFAULT_FILE
) : Disposable {
    private val logger = Logger("Sender", debug)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var flushJob: Job? = null
    private var callback: HeartbeatSenderCallback? = null
    private var flushInterval = flushInterval.coerceAtLeast(Constants.MIN_FLUSH_INTERVAL_MS)

    // Guards requeue against drainPending, so nothing is put back after the queue was drained
    private val queueLock = Any()

    @Volatile
    private var drained = false

    // Heartbeats that failed to send (offline support), oldest first
    private val failedHeartbeats = ConcurrentLinkedDeque<HeartbeatData>()

    @Volatile
    private var isRunning = false

    @Volatile
    var state = DaemonState.DISCONNECTED
        private set

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
     * Stop the periodic flush timer and wait until a flush it was running has queued what it
     * did not deliver again, so nothing reads the queue before that
     */
    suspend fun stopAndJoin() {
        val job = flushJob
        stop()
        job?.cancelAndJoin()
    }

    /**
     * Flush pending heartbeats to the daemon, at most MAX_HEARTBEATS_PER_MESSAGE per message
     *
     * @return How many heartbeats the daemon took, and the state it was found in
     */
    suspend fun flush(): FlushResult {
        // Previously failed heartbeats first (older), then the collector's
        val allHeartbeats = mutableListOf<HeartbeatData>()
        while (true) {
            val hb = failedHeartbeats.pollFirst() ?: break
            allHeartbeats.add(hb)
        }
        allHeartbeats.addAll(collector.flush())

        if (allHeartbeats.isEmpty()) {
            logger.log("No heartbeats to flush")
            updateStatus()
            return FlushResult(0, state)
        }

        logger.log("Flushing ${allHeartbeats.size} heartbeats")

        var sent = 0
        var result = DaemonState.CONNECTED
        try {
            for (chunk in allHeartbeats.chunked(Constants.MAX_HEARTBEATS_PER_MESSAGE)) {
                when (socketClient.sendHeartbeats(HeartbeatPayload(heartbeats = chunk))) {
                    HeartbeatDelivery.OK -> sent += chunk.size
                    HeartbeatDelivery.DISABLED -> {
                        result = DaemonState.DISABLED
                        break
                    }
                    HeartbeatDelivery.FAILED -> {
                        result = DaemonState.DISCONNECTED
                        break
                    }
                }
            }
        } finally {
            // Keep what was not delivered, also when the flush was cancelled mid-send
            requeue(allHeartbeats.subList(sent, allHeartbeats.size))
        }

        state = result
        if (sent == allHeartbeats.size) {
            logger.log("Successfully sent $sent heartbeats")
        } else {
            logger.log("Sent $sent heartbeats, queued ${allHeartbeats.size - sent} for retry ($result)")
        }
        updateStatus()
        return FlushResult(sent, result)
    }

    /**
     * Put heartbeats back ahead of newer ones, dropping the oldest beyond MAX_PENDING_HEARTBEATS
     */
    private fun requeue(heartbeats: List<HeartbeatData>) {
        synchronized(queueLock) {
            if (drained) {
                // The project closed while this flush was sending (a Flush action, or tracking
                // turned off): its queue is already gone, so the daemon sends these later
                OfflineStore.save(heartbeats, offlineFile)
                return
            }
            for (hb in heartbeats.asReversed()) {
                failedHeartbeats.addFirst(hb)
            }
            while (failedHeartbeats.size > Constants.MAX_PENDING_HEARTBEATS) {
                failedHeartbeats.pollFirst()
            }
        }
    }

    /**
     * Force an immediate flush (used by manual flush action)
     */
    fun forceFlush(): FlushResult {
        return runBlocking {
            flush()
        }
    }

    /**
     * Take every heartbeat not yet delivered, oldest first, leaving none queued
     */
    fun drainPending(): List<HeartbeatData> {
        synchronized(queueLock) {
            // From now on a flush still sending leaves what it doesn't deliver in the daemon's file
            drained = true
            val pending = mutableListOf<HeartbeatData>()
            while (true) {
                val hb = failedHeartbeats.pollFirst() ?: break
                pending.add(hb)
            }
            pending.addAll(collector.flush())
            return pending
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
            state = state,
            pendingCount = getPendingCount()
        )
    }

    /**
     * Probe the daemon without sending heartbeats, so the status bar is right before the
     * first flush and while nothing is pending. A running daemon keeps the disabled state
     * until it accepts heartbeats again.
     */
    suspend fun refreshState() {
        val connected = socketClient.getStatus() != null || socketClient.isConnected()
        state = when {
            !connected -> DaemonState.DISCONNECTED
            state == DaemonState.DISABLED -> DaemonState.DISABLED
            else -> DaemonState.CONNECTED
        }
        updateStatus()
    }

    /**
     * Update flush interval
     */
    fun setFlushInterval(interval: Long) {
        val coerced = interval.coerceAtLeast(Constants.MIN_FLUSH_INTERVAL_MS)
        if (coerced == flushInterval) return
        flushInterval = coerced
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
