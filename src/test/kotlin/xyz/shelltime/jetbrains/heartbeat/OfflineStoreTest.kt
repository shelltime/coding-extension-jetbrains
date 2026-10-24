package xyz.shelltime.jetbrains.heartbeat

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class OfflineStoreTest {

    private lateinit var dir: File
    private lateinit var file: File

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("st-offline").toFile()
        file = File(dir, ".shelltime/coding-heartbeat.data.log")
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun savedPayloads(): List<HeartbeatPayload> {
        val json = Json { ignoreUnknownKeys = true }
        return file.readLines().filter { it.isNotBlank() }.map { json.decodeFromString<HeartbeatPayload>(it) }
    }

    @Test
    fun `writes one payload line per message the daemon resends`() {
        assertTrue(OfflineStore.save(testHeartbeats(1_100), file))

        val payloads = savedPayloads()
        assertEquals(listOf(500, 500, 100), payloads.map { it.heartbeats.size })
        assertEquals(testHeartbeats(1_100), payloads.flatMap { it.heartbeats })
    }

    @Test
    fun `appends to what the daemon has not resent yet`() {
        file.parentFile.mkdirs()
        file.writeText("{\"heartbeats\":[]}\n")

        assertTrue(OfflineStore.save(testHeartbeats(2), file))

        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertEquals("{\"heartbeats\":[]}", lines[0])
        assertEquals(listOf("hb-0", "hb-1"), savedPayloads()[1].heartbeats.map { it.heartbeatId })
    }

    @Test
    fun `nothing to save writes no file`() {
        assertTrue(OfflineStore.save(emptyList(), file))
        assertFalse(file.exists())
    }

    @Test
    fun `stops growing a file no daemon reads`() {
        file.parentFile.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(50L * 1024 * 1024 + 1) }

        assertFalse(OfflineStore.save(testHeartbeats(1), file))
        assertEquals(50L * 1024 * 1024 + 1, file.length())
    }
}
