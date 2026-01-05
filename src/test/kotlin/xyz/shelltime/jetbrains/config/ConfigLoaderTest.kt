package xyz.shelltime.jetbrains.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigLoaderTest {

    @Test
    fun `getConfig returns default config when no file exists`() {
        val loader = ConfigLoader()
        val config = loader.getConfig()

        assertTrue(config.enabled)
        assertEquals(Constants.DEFAULT_SOCKET_PATH, config.socketPath)
        assertEquals(Constants.DEFAULT_FLUSH_INTERVAL_MS, config.heartbeatInterval)
        assertFalse(config.debug)
        assertTrue(config.exclude.isEmpty())
    }

    @Test
    fun `reloadConfig returns fresh config`() {
        val loader = ConfigLoader()
        val config1 = loader.getConfig()
        val config2 = loader.reloadConfig()

        // Both should be valid configs
        assertNotNull(config1)
        assertNotNull(config2)
    }

    @Test
    fun `default constants have expected values`() {
        assertEquals("/tmp/shelltime.sock", Constants.DEFAULT_SOCKET_PATH)
        assertEquals(30_000L, Constants.DEBOUNCE_MS)
        assertEquals(120_000L, Constants.DEFAULT_FLUSH_INTERVAL_MS)
        assertEquals(5_000L, Constants.CONNECTION_TIMEOUT_MS)
        assertEquals("shelltime", Constants.PLUGIN_ID)
        assertEquals("jetbrains", Constants.EDITOR_ID)
    }
}
