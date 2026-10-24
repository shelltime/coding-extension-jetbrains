package xyz.shelltime.jetbrains.socket

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.heartbeat.HeartbeatAck
import xyz.shelltime.jetbrains.heartbeat.HeartbeatPayload
import xyz.shelltime.jetbrains.heartbeat.SocketMessage
import xyz.shelltime.jetbrains.heartbeat.StatusResponse
import xyz.shelltime.jetbrains.utils.Logger
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

/**
 * What became of a heartbeat message
 */
enum class HeartbeatDelivery {
    /** The daemon took the heartbeats */
    OK,

    /** The daemon dropped them: code tracking is off in its config */
    DISABLED,

    /** No connection, a timeout, or no reply from a daemon that is stopping */
    FAILED
}

/**
 * Unix domain socket client for communicating with the ShellTime daemon
 */
class SocketClient(
    private var socketPath: String = Constants.DEFAULT_SOCKET_PATH,
    debug: Boolean = false
) {
    private val logger = Logger("SocketClient", debug)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var lastConnectionSuccess = false

    /**
     * Check if we successfully connected on the last attempt
     */
    fun isConnected(): Boolean = lastConnectionSuccess

    /**
     * Update the socket path
     */
    fun setSocketPath(path: String) {
        socketPath = path
    }

    /**
     * Send heartbeats to the daemon
     *
     * @param payload The heartbeat payload to send
     * @return Whether the daemon took the heartbeats
     */
    suspend fun sendHeartbeats(payload: HeartbeatPayload): HeartbeatDelivery {
        val message = SocketMessage(
            type = "heartbeat",
            payload = payload
        )

        val response = send(json.encodeToString(message)) ?: return HeartbeatDelivery.FAILED
        if (response.isBlank()) {
            // Daemons 0.1.42 to 0.1.47 took heartbeats without ever replying (0.1.42 doesn't
            // answer status either), so retrying would resend them forever. A stopping daemon
            // also closes without a reply when it drops a message, but it closes its listener
            // first: one that still takes connections took them.
            return if (testConnection()) HeartbeatDelivery.OK else HeartbeatDelivery.FAILED
        }
        val ack = try {
            json.decodeFromString<HeartbeatAck>(response)
        } catch (e: Exception) {
            // Any other reply means the daemon read the message
            null
        }
        return if (ack?.status == "disabled") HeartbeatDelivery.DISABLED else HeartbeatDelivery.OK
    }

    /**
     * Get status from the daemon
     *
     * @return StatusResponse if successful, null otherwise
     */
    suspend fun getStatus(): StatusResponse? {
        val message = SocketMessage(type = "status")
        val response = send(json.encodeToString(message))

        if (response != null) {
            return try {
                json.decodeFromString<StatusResponse>(response)
            } catch (e: Exception) {
                logger.warn("Failed to parse status response: ${e.message}")
                null
            }
        }
        return null
    }

    /**
     * Send a message to the daemon and receive response
     *
     * @param message The JSON message to send
     * @return The response string, or null if failed
     */
    private suspend fun send(message: String): String? = withContext(Dispatchers.IO) {
        // Blocking socket I/O ignores coroutine cancellation unless it is interruptible, so
        // without it the timeout could not stop a daemon that accepts and never answers
        val response = withTimeoutOrNull(Constants.CONNECTION_TIMEOUT_MS) {
            runInterruptible { exchange(message) }
        }
        if (response == null) {
            lastConnectionSuccess = false
        }
        response
    }

    private fun exchange(message: String): String? {
        return try {
            val socketAddress = UnixDomainSocketAddress.of(socketPath)
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(socketAddress)
                channel.configureBlocking(true)

                // Send message with newline delimiter
                val messageWithNewline = message + "\n"
                val sendBuffer = ByteBuffer.wrap(messageWithNewline.toByteArray(StandardCharsets.UTF_8))
                while (sendBuffer.hasRemaining()) {
                    channel.write(sendBuffer)
                }

                // Read response
                val receiveBuffer = ByteBuffer.allocate(8192)
                val bytesRead = channel.read(receiveBuffer)

                if (bytesRead > 0) {
                    receiveBuffer.flip()
                    val response = StandardCharsets.UTF_8.decode(receiveBuffer).toString().trim()
                    lastConnectionSuccess = true
                    logger.log("Sent message, received: $response")
                    response
                } else {
                    lastConnectionSuccess = true
                    logger.log("Sent message, no response received")
                    ""
                }
            }
        } catch (e: Exception) {
            lastConnectionSuccess = false
            logger.log("Failed to connect to daemon: ${e.message}")
            null
        }
    }

    /**
     * Test connection to the daemon
     *
     * @return True if connected, false otherwise
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val socketAddress = UnixDomainSocketAddress.of(socketPath)
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                withTimeoutOrNull(Constants.CONNECTION_TIMEOUT_MS) {
                    channel.connect(socketAddress)
                    lastConnectionSuccess = true
                    true
                } ?: run {
                    lastConnectionSuccess = false
                    false
                }
            }
        } catch (e: Exception) {
            lastConnectionSuccess = false
            logger.log("Connection test failed: ${e.message}")
            false
        }
    }

    /**
     * Set debug logging
     */
    fun setDebug(enabled: Boolean) {
        logger.setDebug(enabled)
    }
}
