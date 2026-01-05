package xyz.shelltime.jetbrains.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SystemUtilsTest {

    @Test
    fun `getOSName returns valid OS name`() {
        val osName = SystemUtils.getOSName()
        assertTrue(osName in listOf("macOS", "Windows", "Linux", "Unknown"))
    }

    @Test
    fun `getOSVersion returns non-empty string`() {
        val osVersion = SystemUtils.getOSVersion()
        assertTrue(osVersion.isNotEmpty())
    }

    @Test
    fun `getMachineName returns non-empty string`() {
        val machineName = SystemUtils.getMachineName()
        assertTrue(machineName.isNotEmpty())
    }

    @Test
    fun `generateUUID returns valid UUID format`() {
        val uuid = SystemUtils.generateUUID()
        assertTrue(uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `generateUUID returns unique values`() {
        val uuid1 = SystemUtils.generateUUID()
        val uuid2 = SystemUtils.generateUUID()
        assertNotEquals(uuid1, uuid2)
    }
}
