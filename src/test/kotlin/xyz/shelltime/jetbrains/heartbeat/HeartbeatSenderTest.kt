package xyz.shelltime.jetbrains.heartbeat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.socket.HeartbeatDelivery
import xyz.shelltime.jetbrains.socket.HeartbeatTransport
import java.io.File
import java.nio.file.Files

/**
 * The collector's queue, filled by the tests
 */
private class FakeSource : HeartbeatSource {
    private val collected = ArrayDeque<HeartbeatData>()

    @Synchronized
    fun collect(heartbeats: List<HeartbeatData>) = collected.addAll(heartbeats)

    @Synchronized
    override fun flush(): List<HeartbeatData> = collected.toList().also { collected.clear() }

    @Synchronized
    override fun getPendingCount(): Int = collected.size
}

/**
 * A daemon that answers each message with the next reply (OK once they run out), or with send,
 * which suspends in the flush's own coroutine
 */
private class FakeDaemon : HeartbeatTransport {
    private val messages = mutableListOf<List<String>>()
    val replies = ArrayDeque<HeartbeatDelivery>()

    @Volatile
    var send: (suspend () -> HeartbeatDelivery)? = null

    @Volatile
    var status: StatusResponse? = null

    @Volatile
    var connected = false

    /** The heartbeat ids of each message, in the order they were sent */
    val sentMessages: List<List<String>>
        @Synchronized get() = messages.toList()

    override suspend fun sendHeartbeats(payload: HeartbeatPayload): HeartbeatDelivery {
        val reply = synchronized(this) {
            messages.add(payload.heartbeats.map { it.heartbeatId })
            replies.removeFirstOrNull()
        }
        return send?.invoke() ?: reply ?: HeartbeatDelivery.OK
    }

    override suspend fun getStatus(): StatusResponse? = status

    override fun isConnected(): Boolean = connected
}

class HeartbeatSenderTest {

    private lateinit var source: FakeSource
    private lateinit var daemon: FakeDaemon
    private lateinit var sender: HeartbeatSender
    private lateinit var offlineFile: File

    @BeforeEach
    fun setUp() {
        source = FakeSource()
        daemon = FakeDaemon()
        // Never the daemon's real file: a running daemon would upload what lands there
        offlineFile = File(Files.createTempDirectory("st-sender").toFile(), "coding-heartbeat.data.log")
        sender = HeartbeatSender(source, daemon, offlineFile = offlineFile)
    }

    @AfterEach
    fun tearDown() {
        sender.dispose()
        offlineFile.parentFile.deleteRecursively()
    }

    private fun collect(heartbeats: List<HeartbeatData>) = source.collect(heartbeats)

    @Test
    fun `large backlog is sent in messages of at most MAX_HEARTBEATS_PER_MESSAGE`() = runBlocking {
        collect(testHeartbeats(1_200))

        val result = sender.flush()

        assertEquals(FlushResult(1_200, DaemonState.CONNECTED), result)
        assertEquals(listOf(500, 500, 200), daemon.sentMessages.map { it.size })
        assertEquals(testHeartbeats(1_200).map { it.heartbeatId }, daemon.sentMessages.flatten())
        assertEquals(0, sender.getPendingCount())
    }

    @Test
    fun `failed message keeps it and the rest for the next flush, oldest first`() = runBlocking {
        collect(testHeartbeats(1_200))
        daemon.replies.addAll(listOf(HeartbeatDelivery.OK, HeartbeatDelivery.FAILED))

        val first = sender.flush()

        assertEquals(FlushResult(500, DaemonState.DISCONNECTED), first)
        assertEquals(DaemonState.DISCONNECTED, sender.state)
        assertEquals(700, sender.getPendingCount())

        collect(testHeartbeats(2, prefix = "new"))
        val second = sender.flush()

        assertEquals(FlushResult(702, DaemonState.CONNECTED), second)
        val expected = testHeartbeats(1_200).drop(500).map { it.heartbeatId } + listOf("new-0", "new-1")
        assertEquals(expected, daemon.sentMessages.drop(2).flatten())
        assertEquals(0, sender.getPendingCount())
    }

    @Test
    fun `disabled daemon keeps the heartbeats and reports the disabled state`() = runBlocking {
        val states = mutableListOf<DaemonState>()
        sender.setCallback(object : HeartbeatSenderCallback {
            override fun onStatusChanged(state: DaemonState, pendingCount: Int) {
                states.add(state)
            }
        })
        collect(testHeartbeats(3))
        daemon.replies.add(HeartbeatDelivery.DISABLED)

        val result = sender.flush()

        assertEquals(FlushResult(0, DaemonState.DISABLED), result)
        assertEquals(3, sender.getPendingCount())
        assertEquals(listOf(DaemonState.DISABLED), states)
    }

    @Test
    fun `queue keeps the newest MAX_PENDING_HEARTBEATS while the daemon is down`() = runBlocking {
        daemon.replies.addAll(List(20) { HeartbeatDelivery.FAILED })
        collect(testHeartbeats(Constants.MAX_PENDING_HEARTBEATS, prefix = "old"))
        sender.flush()
        collect(testHeartbeats(10, prefix = "new"))
        sender.flush()

        assertEquals(Constants.MAX_PENDING_HEARTBEATS, sender.getPendingCount())
        val pending = sender.drainPending().map { it.heartbeatId }
        assertEquals("old-10", pending.first())
        assertEquals("new-9", pending.last())
    }

    @Test
    fun `cancelled flush keeps what was not delivered`() {
        collect(testHeartbeats(1_200))
        var calls = 0
        daemon.send = {
            calls++
            if (calls == 2) throw CancellationException("IDE closing")
            HeartbeatDelivery.OK
        }

        assertThrows<CancellationException> { runBlocking { sender.flush() } }

        assertEquals(700, sender.getPendingCount())
    }

    @Test
    @Timeout(10)
    fun `stopAndJoin waits for a periodic flush cancelled mid-send to put its heartbeats back`() {
        collect(testHeartbeats(3))
        val sending = CompletableDeferred<Unit>()
        daemon.send = {
            sending.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                // Closing the interrupted socket takes a moment
                withContext(NonCancellable) { delay(200) }
            }
        }
        // MIN_FLUSH_INTERVAL_MS would make the test wait 10 s for the first periodic flush
        HeartbeatSender::class.java.getDeclaredField("flushInterval")
            .apply { isAccessible = true }
            .setLong(sender, 10)
        sender.start()

        runBlocking {
            sending.await()
            sender.stopAndJoin()
        }

        assertEquals(listOf("hb-0", "hb-1", "hb-2"), sender.drainPending().map { it.heartbeatId })
    }

    @Test
    @Timeout(10)
    fun `a flush that fails after the queue was drained leaves its heartbeats in the daemon's file`() {
        // A Flush action still sending when the project closes
        collect(testHeartbeats(3))
        val sending = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<HeartbeatDelivery>()
        daemon.send = {
            sending.complete(Unit)
            reply.await()
        }

        runBlocking {
            val flush = async { sender.flush() }
            sending.await()
            assertEquals(emptyList<HeartbeatData>(), sender.drainPending())
            reply.complete(HeartbeatDelivery.FAILED)
            flush.await()
        }

        assertEquals(0, sender.getPendingCount())
        val saved = offlineFile.readLines().flatMap {
            Json.decodeFromString(HeartbeatPayload.serializer(), it).heartbeats
        }
        assertEquals(listOf("hb-0", "hb-1", "hb-2"), saved.map { it.heartbeatId })
    }

    @Test
    fun `drainPending returns undelivered heartbeats before newly collected ones and empties the queue`() = runBlocking {
        collect(testHeartbeats(2, prefix = "old"))
        daemon.replies.add(HeartbeatDelivery.FAILED)
        sender.flush()
        collect(testHeartbeats(1, prefix = "new"))

        val drained = sender.drainPending().map { it.heartbeatId }

        assertEquals(listOf("old-0", "old-1", "new-0"), drained)
        assertEquals(0, sender.getPendingCount())
    }

    @Test
    fun `empty flush sends nothing`() = runBlocking {
        val result = sender.flush()

        assertEquals(0, result.sent)
        assertEquals(emptyList<List<String>>(), daemon.sentMessages)
    }

    @Test
    fun `refreshState keeps the disabled state while the daemon answers`() = runBlocking {
        collect(testHeartbeats(1))
        daemon.replies.add(HeartbeatDelivery.DISABLED)
        sender.flush()
        daemon.status = StatusResponse(version = "0.1.95")

        sender.refreshState()
        assertEquals(DaemonState.DISABLED, sender.state)

        daemon.status = null
        daemon.connected = false
        sender.refreshState()
        assertEquals(DaemonState.DISCONNECTED, sender.state)
    }
}
