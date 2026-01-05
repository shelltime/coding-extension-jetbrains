package xyz.shelltime.jetbrains.socket

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.shelltime.jetbrains.config.Constants
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
     * @return True if successfully sent, false otherwise
     */
    suspend fun sendHeartbeats(payload: HeartbeatPayload): Boolean {
        val message = SocketMessage(
            type = "heartbeat",
            payload = payload
        )

        val response = send(json.encodeToString(message))
        return response != null
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
        withTimeoutOrNull(Constants.CONNECTION_TIMEOUT_MS) {
            try {
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
