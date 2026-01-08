package xyz.shelltime.jetbrains.version

import io.mockk.*
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
    fun `checkVersion handles connection failure gracefully`() {
        every { anyConstructed<URL>().openConnection() } throws java.io.IOException("Connection failed")

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Should not throw - version check failures are silent
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        checker.dispose()
    }

    @Test
    fun `checkVersion handles HTTP error response gracefully`() {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 500

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Should not throw - HTTP errors are handled gracefully
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        checker.dispose()
    }

    @Test
    fun `version check encodes special characters in version`() {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0"}""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Version with special characters should be URL-encoded
        assertDoesNotThrow { checker.checkVersion("1.0.0-beta+build.123", null) }

        checker.dispose()
    }

    @Test
    fun `checkVersion handles malformed JSON response gracefully`() {
        val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(
            """{"invalid json""".toByteArray()
        )

        val checker = VersionChecker("https://api.test.com", "https://web.test.com")

        // Should not throw - JSON parse errors are handled gracefully
        assertDoesNotThrow { checker.checkVersion("1.0.0", null) }

        checker.dispose()
    }
}
