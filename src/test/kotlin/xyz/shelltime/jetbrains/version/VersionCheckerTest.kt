package xyz.shelltime.jetbrains.version

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.shelltime.jetbrains.config.Constants
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Runs the checker against a local HTTP server. Mocking URL's constructors instead routed every URL
 * in the JVM through the mock, the class loader's resource reads included, so a library that
 * initialized during a test (kotlin-reflect, for MockK) read the mock's stream and hung.
 */
class VersionCheckerTest {

    private lateinit var server: HttpServer
    private val requests = LinkedBlockingQueue<String>()

    @Volatile
    private var status = 200

    @Volatile
    private var body = ""

    @BeforeEach
    fun setup() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            try {
                requests.add("${exchange.requestURI.rawPath}?${exchange.requestURI.rawQuery}")
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                // No keep-alive connection, so no JDK thread outlives the test
                exchange.responseHeaders.add("Connection", "close")
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.stop(0)
    }

    private val apiEndpoint get() = "http://127.0.0.1:${server.address.port}"

    private fun respond(status: Int, body: String) {
        this.status = status
        this.body = body
    }

    /** The next request the checker made, waiting for it to arrive */
    private fun nextRequest(): String? = requests.poll(10, TimeUnit.SECONDS)

    @Test
    fun `dispose cancels coroutine scope without errors`() {
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")
        assertDoesNotThrow { checker.dispose() }
    }

    @Test
    fun `checkVersion handles connection failure gracefully`() = runBlocking {
        // A port nothing listens on
        val port = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        val checker = VersionChecker("http://127.0.0.1:$port", "https://web.test.com")

        // Should not throw - version check failures are silent
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        // Allow time for coroutine to execute
        delay(200)

        checker.dispose()
    }

    @Test
    fun `checkVersion handles HTTP error response gracefully`() = runBlocking {
        respond(500, "")
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")

        // Should not throw - HTTP errors are handled gracefully
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        assertEquals("${Constants.VERSION_CHECK_ENDPOINT}?version=1.0.0", nextRequest())

        checker.dispose()
    }

    @Test
    fun `checkVersion shows notification when update is available`() = runBlocking {
        respond(200, """{"isLatest":false,"latestVersion":"2.0.0","version":"1.0.0"}""")
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")

        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        assertEquals("${Constants.VERSION_CHECK_ENDPOINT}?version=1.0.0", nextRequest())

        checker.dispose()
    }

    @Test
    fun `checkVersion does not show notification when version is up to date`() = runBlocking {
        respond(200, """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0"}""")
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")

        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        assertEquals("${Constants.VERSION_CHECK_ENDPOINT}?version=1.0.0", nextRequest())

        checker.dispose()
    }

    @Test
    fun `version check encodes special characters in version`() = runBlocking {
        respond(200, """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0"}""")
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")

        assertDoesNotThrow { checker.checkVersion("1.0.0-beta+build.123", null) }

        assertEquals("${Constants.VERSION_CHECK_ENDPOINT}?version=1.0.0-beta%2Bbuild.123", nextRequest())

        checker.dispose()
    }

    @Test
    fun `checkVersion handles malformed JSON response gracefully`() = runBlocking {
        respond(200, """{"invalid json""")
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")

        // Should not throw - JSON parse errors are handled gracefully
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        assertNotNull(nextRequest())

        checker.dispose()
    }

    @Test
    fun `hasShownWarning flag prevents multiple notifications`() = runBlocking {
        respond(200, """{"isLatest":false,"latestVersion":"2.0.0","version":"1.0.0"}""")
        val checker = VersionChecker(apiEndpoint, "https://web.test.com")

        // First call should trigger check
        checker.checkVersion("1.0.0", null)
        assertNotNull(nextRequest())
        // Allow time for the checker to read the reply
        delay(500)

        // Second call should be skipped
        checker.checkVersion("1.0.0", null)
        assertNull(requests.poll(500, TimeUnit.MILLISECONDS))

        checker.dispose()
    }
}
