package xyz.shelltime.jetbrains.heartbeat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.socket.HeartbeatDelivery
import xyz.shelltime.jetbrains.socket.SocketClient

class HeartbeatSenderTest {

    private val collected = ArrayDeque<HeartbeatData>()
    private val sentMessages = mutableListOf<List<String>>()
    private val replies = ArrayDeque<HeartbeatDelivery>()

    private lateinit var collector: HeartbeatCollector
    private lateinit var socket: SocketClient
    private lateinit var sender: HeartbeatSender

    @BeforeEach
    fun setUp() {
        collector = mockk(relaxed = true)
        every { collector.flush() } answers {
            val all = collected.toList()
            collected.clear()
            all
        }
        every { collector.getPendingCount() } answers { collected.size }

        socket = mockk(relaxed = true)
        coEvery { socket.sendHeartbeats(any()) } answers {
            sentMessages.add(firstArg<HeartbeatPayload>().heartbeats.map { it.heartbeatId })
            replies.removeFirstOrNull() ?: HeartbeatDelivery.OK
        }

        sender = HeartbeatSender(collector, socket)
    }

    @AfterEach
    fun tearDown() {
        sender.dispose()
    }

    private fun collect(heartbeats: List<HeartbeatData>) = collected.addAll(heartbeats)

    @Test
    fun `large backlog is sent in messages of at most MAX_HEARTBEATS_PER_MESSAGE`() = runBlocking {
        collect(testHeartbeats(1_200))

        val result = sender.flush()

        assertEquals(FlushResult(1_200, DaemonState.CONNECTED), result)
        assertEquals(listOf(500, 500, 200), sentMessages.map { it.size })
        assertEquals(testHeartbeats(1_200).map { it.heartbeatId }, sentMessages.flatten())
        assertEquals(0, sender.getPendingCount())
    }

    @Test
    fun `failed message keeps it and the rest for the next flush, oldest first`() = runBlocking {
        collect(testHeartbeats(1_200))
        replies.addAll(listOf(HeartbeatDelivery.OK, HeartbeatDelivery.FAILED))

        val first = sender.flush()

        assertEquals(FlushResult(500, DaemonState.DISCONNECTED), first)
        assertEquals(DaemonState.DISCONNECTED, sender.state)
        assertEquals(700, sender.getPendingCount())

        collect(testHeartbeats(2, prefix = "new"))
        sentMessages.clear()
        val second = sender.flush()

        assertEquals(FlushResult(702, DaemonState.CONNECTED), second)
        val expected = testHeartbeats(1_200).drop(500).map { it.heartbeatId } + listOf("new-0", "new-1")
        assertEquals(expected, sentMessages.flatten())
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
        replies.add(HeartbeatDelivery.DISABLED)

        val result = sender.flush()

        assertEquals(FlushResult(0, DaemonState.DISABLED), result)
        assertEquals(3, sender.getPendingCount())
        assertEquals(listOf(DaemonState.DISABLED), states)
    }

    @Test
    fun `queue keeps the newest MAX_PENDING_HEARTBEATS while the daemon is down`() = runBlocking {
        replies.addAll(List(20) { HeartbeatDelivery.FAILED })
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
        coEvery { socket.sendHeartbeats(any()) } answers {
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
        coEvery { socket.sendHeartbeats(any()) } coAnswers {
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
    fun `drainPending returns undelivered heartbeats before newly collected ones and empties the queue`() = runBlocking {
        collect(testHeartbeats(2, prefix = "old"))
        replies.add(HeartbeatDelivery.FAILED)
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
        coVerify(exactly = 0) { socket.sendHeartbeats(any()) }
    }

    @Test
    fun `refreshState keeps the disabled state while the daemon answers`() = runBlocking {
        collect(testHeartbeats(1))
        replies.add(HeartbeatDelivery.DISABLED)
        sender.flush()
        coEvery { socket.getStatus() } returns StatusResponse(version = "0.1.95")

        sender.refreshState()
        assertEquals(DaemonState.DISABLED, sender.state)

        coEvery { socket.getStatus() } returns null
        every { socket.isConnected() } returns false
        sender.refreshState()
        assertEquals(DaemonState.DISCONNECTED, sender.state)
    }
}
