package xyz.shelltime.jetbrains.heartbeat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HeartbeatDataTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `HeartbeatData serializes to JSON correctly`() {
        val heartbeat = HeartbeatData(
            heartbeatId = "test-uuid-1234",
            entity = "/path/to/file.kt",
            entityType = "file",
            category = "coding",
            time = 1704067200,
            project = "my-project",
            projectRootPath = "/path/to/project",
            branch = "main",
            language = "kotlin",
            lines = 100,
            lineNumber = 42,
            cursorPosition = 10,
            editor = "jetbrains",
            editorVersion = "2024.1",
            plugin = "shelltime",
            pluginVersion = "0.0.1",
            machine = "my-machine",
            os = "macOS",
            osVersion = "14.0",
            isWrite = false
        )

        val jsonString = json.encodeToString(heartbeat)

        assertTrue(jsonString.contains("\"heartbeatId\":\"test-uuid-1234\""))
        assertTrue(jsonString.contains("\"entity\":\"/path/to/file.kt\""))
        assertTrue(jsonString.contains("\"entityType\":\"file\""))
        assertTrue(jsonString.contains("\"category\":\"coding\""))
        assertTrue(jsonString.contains("\"editor\":\"jetbrains\""))
        assertTrue(jsonString.contains("\"plugin\":\"shelltime\""))
        assertTrue(jsonString.contains("\"isWrite\":false"))
    }

    @Test
    fun `HeartbeatPayload serializes correctly`() {
        val heartbeat = HeartbeatData(
            heartbeatId = "test-uuid",
            entity = "/file.kt",
            entityType = "file",
            category = "coding",
            time = 1704067200,
            project = "project",
            projectRootPath = "/project",
            branch = "main",
            language = "kotlin",
            editor = "jetbrains",
            editorVersion = "2024.1",
            plugin = "shelltime",
            pluginVersion = "0.0.1",
            machine = "machine",
            os = "macOS",
            osVersion = "14.0",
            isWrite = true
        )

        val payload = HeartbeatPayload(heartbeats = listOf(heartbeat))
        val jsonString = json.encodeToString(payload)

        assertTrue(jsonString.contains("\"heartbeats\":["))
        assertTrue(jsonString.contains("\"heartbeatId\":\"test-uuid\""))
    }

    @Test
    fun `SocketMessage serializes correctly for heartbeat type`() {
        val payload = HeartbeatPayload(heartbeats = emptyList())
        val message = SocketMessage(type = "heartbeat", payload = payload)
        val jsonString = json.encodeToString(message)

        assertTrue(jsonString.contains("\"type\":\"heartbeat\""))
        assertTrue(jsonString.contains("\"payload\":{"))
    }

    @Test
    fun `SocketMessage serializes correctly for status type`() {
        val message = SocketMessage(type = "status")
        val jsonString = json.encodeToString(message)

        assertTrue(jsonString.contains("\"type\":\"status\""))
    }

    @Test
    fun `StatusResponse deserializes correctly`() {
        val jsonString = """{"version":"1.0.0","uptime":3600,"platform":"darwin/arm64","goVersion":"1.21"}"""
        val response = json.decodeFromString<StatusResponse>(jsonString)

        assertEquals("1.0.0", response.version)
        assertEquals(3600L, response.uptime)
        assertEquals("darwin/arm64", response.platform)
        assertEquals("1.21", response.goVersion)
    }

    @Test
    fun `StatusResponse handles missing fields`() {
        val jsonString = """{"version":"1.0.0"}"""
        val response = json.decodeFromString<StatusResponse>(jsonString)

        assertEquals("1.0.0", response.version)
        assertNull(response.uptime)
        assertNull(response.platform)
        assertNull(response.goVersion)
    }

    @Test
    fun `VersionCheckResponse deserializes correctly with update available`() {
        val jsonString = """{"isLatest":false,"latestVersion":"2.0.0","version":"1.0.0"}"""
        val response = json.decodeFromString<VersionCheckResponse>(jsonString)

        assertFalse(response.isLatest)
        assertEquals("2.0.0", response.latestVersion)
        assertEquals("1.0.0", response.version)
    }

    @Test
    fun `VersionCheckResponse deserializes correctly when up to date`() {
        val jsonString = """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0"}"""
        val response = json.decodeFromString<VersionCheckResponse>(jsonString)

        assertTrue(response.isLatest)
        assertEquals("1.0.0", response.latestVersion)
        assertEquals("1.0.0", response.version)
    }

    @Test
    fun `VersionCheckResponse serializes correctly`() {
        val response = VersionCheckResponse(isLatest = false, latestVersion = "2.0.0", version = "1.0.0")
        val jsonString = json.encodeToString(response)

        // Deserialize back to verify correct JSON structure
        val deserialized = json.decodeFromString<VersionCheckResponse>(jsonString)
        assertEquals(response, deserialized)
        assertFalse(deserialized.isLatest)
        assertEquals("2.0.0", deserialized.latestVersion)
        assertEquals("1.0.0", deserialized.version)
    }

    @Test
    fun `VersionCheckResponse ignores unknown fields`() {
        val jsonString = """{"isLatest":true,"latestVersion":"1.0.0","version":"1.0.0","unknownField":"value"}"""
        val response = json.decodeFromString<VersionCheckResponse>(jsonString)

        // Verify unknown field was ignored and known fields were parsed correctly
        assertTrue(response.isLatest)
        assertEquals("1.0.0", response.latestVersion)
        assertEquals("1.0.0", response.version)
    }
}
