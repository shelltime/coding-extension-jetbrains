package xyz.shelltime.jetbrains.socket

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import xyz.shelltime.jetbrains.heartbeat.HeartbeatPayload
import xyz.shelltime.jetbrains.heartbeat.testHeartbeats
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class SocketClientTest {

    private lateinit var dir: File
    private lateinit var socketPath: String
    private var server: ServerSocketChannel? = null
    private val received = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("st-sock").toFile()
        socketPath = File(dir, "d.sock").absolutePath
    }

    @AfterEach
    fun tearDown() {
        server?.close()
        dir.deleteRecursively()
    }

    /**
     * A daemon that reads one line per connection and answers it with [reply], or never
     * answers when [reply] is null
     */
    private fun startDaemon(reply: String?) {
        val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        channel.bind(UnixDomainSocketAddress.of(socketPath))
        server = channel
        Thread {
            while (channel.isOpen) {
                val conn = try {
                    channel.accept()
                } catch (e: Exception) {
                    return@Thread
                }
                Thread { serve(conn, reply) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun serve(conn: SocketChannel, reply: String?) {
        conn.use {
            val line = StringBuilder()
            val buffer = ByteBuffer.allocate(64 * 1024)
            while (!line.endsWith("\n")) {
                buffer.clear()
                if (conn.read(buffer) < 0) return
                buffer.flip()
                line.append(StandardCharsets.UTF_8.decode(buffer))
            }
            received.add(line.toString().trim())
            if (reply == null) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30))
                return
            }
            conn.write(ByteBuffer.wrap((reply + "\n").toByteArray(StandardCharsets.UTF_8)))
        }
    }

    private fun payload(count: Int) = HeartbeatPayload(heartbeats = testHeartbeats(count))

    @Test
    fun `ok reply delivers the heartbeats as one heartbeat message`() = runBlocking {
        startDaemon("""{"status":"ok"}""")
        val client = SocketClient(socketPath)

        assertEquals(HeartbeatDelivery.OK, client.sendHeartbeats(payload(3)))
        assertTrue(client.isConnected())

        assertEquals(1, received.size)
        val message = Json.parseToJsonElement(received[0]).jsonObject
        assertEquals("heartbeat", message["type"]!!.jsonPrimitive.content)
        val heartbeats = message["payload"]!!.jsonObject["heartbeats"]!!.jsonArray
        assertEquals(listOf("hb-0", "hb-1", "hb-2"), heartbeats.map { it.jsonObject["heartbeatId"]!!.jsonPrimitive.content })
    }

    @Test
    fun `disabled reply is not a delivery`() = runBlocking {
        startDaemon("""{"status":"disabled"}""")
        val client = SocketClient(socketPath)

        assertEquals(HeartbeatDelivery.DISABLED, client.sendHeartbeats(payload(1)))
        // The daemon runs, it only drops editor heartbeats
        assertTrue(client.isConnected())
    }

    @Test
    fun `reply from an older daemon without a status still counts as delivered`() = runBlocking {
        startDaemon("ok")
        val client = SocketClient(socketPath)

        assertEquals(HeartbeatDelivery.OK, client.sendHeartbeats(payload(1)))
    }

    @Test
    fun `no daemon fails`() = runBlocking {
        val client = SocketClient(socketPath)

        assertEquals(HeartbeatDelivery.FAILED, client.sendHeartbeats(payload(1)))
        assertFalse(client.isConnected())
    }

    @Test
    @Timeout(15)
    fun `daemon that never answers fails after the connection timeout`() = runBlocking {
        startDaemon(null)
        val client = SocketClient(socketPath)

        val started = System.nanoTime()
        assertEquals(HeartbeatDelivery.FAILED, client.sendHeartbeats(payload(1)))
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertTrue(elapsedMs < 10_000, "took ${elapsedMs}ms")
        assertFalse(client.isConnected())
    }

    @Test
    fun `status reply is parsed`() = runBlocking {
        startDaemon("""{"version":"0.1.95","uptime":"1h2m3s","startedAt":"2026-10-24T08:00:00Z"}""")
        val client = SocketClient(socketPath)

        val status = client.getStatus()
        assertNotNull(status)
        assertEquals("0.1.95", status!!.version)
        assertEquals("1h2m3s", status.uptime)
    }
}
