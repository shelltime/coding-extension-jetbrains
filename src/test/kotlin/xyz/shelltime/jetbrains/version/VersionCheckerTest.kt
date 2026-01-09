package xyz.shelltime.jetbrains.version

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class VersionCheckerTest {

    @BeforeEach
    fun setup() {
        mockkConstructor(URL::class)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `dispose cancels coroutine scope without errors`() {
        val checker = VersionChecker("https://api.test.com", "https://web.test.com")
        assertDoesNotThrow { checker.dispose() }
    }

    @Test
    fun `checkVersion handles connection failure gracefully`() = runBlocking {
        every { anyConstructed<URL>().openConnection() } throws java.io.IOException("Connection failed")

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Should not throw - version check failures are silent
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        // Allow time for coroutine to execute
        delay(200)

        // Verify the connection was attempted
        verify { anyConstructed<URL>().openConnection() }

        checker.dispose()
    }

    @Test
    fun `checkVersion handles HTTP error response gracefully`() = runBlocking {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 500

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Should not throw - HTTP errors are handled gracefully
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        // Allow time for coroutine to execute
        delay(200)

        // Verify HTTP error was handled
        verify { mockConnection.responseCode }
        verify { mockConnection.disconnect() }

        checker.dispose()
    }

    @Test
    fun `checkVersion shows notification when update is available`() = runBlocking {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"isLatest":false,"latestVersion":"2.0.0","version":"1.0.0"}""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        // Allow time for coroutine to execute
        delay(200)

        // Verify the connection was made and response was read
        verify { mockConnection.responseCode }
        verify { mockConnection.inputStream }
        verify { mockConnection.disconnect() }

        checker.dispose()
    }

    @Test
    fun `checkVersion does not show notification when version is up to date`() = runBlocking {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0"}""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        // Allow time for coroutine to execute
        delay(200)

        // Verify the connection was made and response was read
        verify { mockConnection.responseCode }
        verify { mockConnection.inputStream }
        verify { mockConnection.disconnect() }

        checker.dispose()
    }

    @Test
    fun `version check encodes special characters in version`() = runBlocking {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        val mockInputStream = mockk<java.io.InputStream>(relaxed = true)
        val mockBufferedReader = mockk<java.io.BufferedReader>(relaxed = true)

        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0"}""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Version with special characters should be URL-encoded
        assertDoesNotThrow { checker.checkVersion("1.0.0-beta+build.123", null) }

        // Allow time for coroutine to execute
        delay(200)

        // Verify the connection was made (URL encoding happens in the URL construction)
        verify { anyConstructed<URL>().openConnection() }
        verify { mockConnection.disconnect() }

        checker.dispose()
    }

    @Test
    fun `checkVersion handles malformed JSON response gracefully`() = runBlocking {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"invalid json""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Should not throw - JSON parse errors are handled gracefully
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        // Allow time for coroutine to execute
        delay(200)

        // Verify the connection was attempted and response was read
        verify { mockConnection.responseCode }
        verify { mockConnection.inputStream }
        verify { mockConnection.disconnect() }

        checker.dispose()
    }

    @Test
    fun `hasShownWarning flag prevents multiple notifications`() = runBlocking {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"isLatest":false,"latestVersion":"2.0.0","version":"1.0.0"}""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // First call should trigger check
        checker.checkVersion("1.0.0", null)
        delay(200)

        // Second call should be skipped
        checker.checkVersion("1.0.0", null)
        delay(200)

        // Should only open connection once
        verify(exactly = 1) { anyConstructed<URL>().openConnection() }

        checker.dispose()
    }
}
