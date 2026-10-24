package xyz.shelltime.jetbrains.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfigDiscoveryTest {

    @TempDir
    lateinit var dir: File

    private fun write(name: String, content: String) = File(dir, name).writeText(content)

    @Test
    fun `reads config yaml, which shelltime init writes`() {
        write("config.yaml", "socketPath: /tmp/yaml.sock\ncodeTracking:\n  enabled: false\n")

        val config = ConfigLoader(dir.path).getConfig()

        assertEquals("/tmp/yaml.sock", config.socketPath)
        assertFalse(config.enabled)
    }

    @Test
    fun `prefers config yaml over config yml, like the CLI`() {
        write("config.yml", "socketPath: /tmp/yml.sock\n")
        write("config.yaml", "socketPath: /tmp/yaml.sock\n")

        assertEquals("/tmp/yaml.sock", ConfigLoader(dir.path).getConfig().socketPath)
    }

    @Test
    fun `merges config local over the base file`() {
        write("config.yaml", "socketPath: /tmp/base.sock\nwebEndpoint: https://shelltime.xyz\ncodeTracking:\n  enabled: false\n")
        write("config.local.yaml", "socketPath: /tmp/local.sock\ncodeTracking:\n  enabled: true\n")

        val config = ConfigLoader(dir.path).getConfig()

        assertEquals("/tmp/local.sock", config.socketPath)
        assertEquals("https://shelltime.xyz", config.webEndpoint)
        assertTrue(config.enabled)
    }

    @Test
    fun `a local file that only sets the token keeps tracking as the base says`() {
        write("config.yaml", "codeTracking:\n  enabled: false\n")
        write("config.local.yaml", "codeTracking:\n  token: secret\n")

        assertFalse(ConfigLoader(dir.path).getConfig().enabled)
    }

    @Test
    fun `reloads when a file changes`() {
        val loader = ConfigLoader(dir.path)
        write("config.yaml", "socketPath: /tmp/first.sock\n")
        assertEquals("/tmp/first.sock", loader.getConfig().socketPath)

        write("config.local.yaml", "socketPath: /tmp/second.sock\n")
        assertEquals("/tmp/second.sock", loader.getConfig().socketPath)
    }

    @Test
    fun `defaults without any config file`() {
        val config = ConfigLoader(dir.path).getConfig()

        assertTrue(config.enabled)
        assertEquals(Constants.DEFAULT_SOCKET_PATH, config.socketPath)
    }
}
