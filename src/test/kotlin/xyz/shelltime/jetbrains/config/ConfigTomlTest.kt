package xyz.shelltime.jetbrains.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfigTomlTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `reads the keys the CLI writes to config toml`() {
        File(dir, "config.toml").writeText(
            "APIEndpoint = \"http://localhost:27312\"\nWebEndpoint = \"http://localhost:3302\"\n" +
                "socketPath = \"/tmp/toml.sock\"\n\n[codeTracking]\nenabled = true\n"
        )

        val config = ConfigLoader(dir.path).getConfig()

        assertEquals("http://localhost:27312", config.apiEndpoint)
        assertEquals("http://localhost:3302", config.webEndpoint)
        assertEquals("/tmp/toml.sock", config.socketPath)
        assertTrue(config.enabled)
    }

    @Test
    fun `config yaml wins over an older config toml`() {
        File(dir, "config.toml").writeText("socketPath = \"/tmp/old.sock\"\n")
        File(dir, "config.yaml").writeText("socketPath: /tmp/new.sock\n")

        assertEquals("/tmp/new.sock", ConfigLoader(dir.path).getConfig().socketPath)
    }
}
