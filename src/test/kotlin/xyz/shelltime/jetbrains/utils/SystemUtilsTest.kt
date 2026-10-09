package xyz.shelltime.jetbrains.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

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
    fun `getMachineName is stable and has no surrounding whitespace`() {
        val machineName = SystemUtils.getMachineName()
        assertEquals(machineName, SystemUtils.getMachineName())
        assertEquals(machineName.trim(), machineName)
    }

    @Test
    fun `getMachineName matches the kernel hostname on Linux`() {
        // The CLI daemon and the other editor plugins report gethostname(), and the
        // server groups sessions per machine, so the names must be identical
        val kernelHostname = File("/proc/sys/kernel/hostname")
        assumeTrue(kernelHostname.isFile)
        assertEquals(kernelHostname.readText().trim(), SystemUtils.getMachineName())
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
