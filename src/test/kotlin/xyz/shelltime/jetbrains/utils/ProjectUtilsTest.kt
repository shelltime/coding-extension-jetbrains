package xyz.shelltime.jetbrains.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProjectUtilsTest {

    @Test
    fun `shouldExclude returns true for git directory`() {
        assertTrue(ProjectUtils.shouldExclude("/project/.git/config"))
        assertTrue(ProjectUtils.shouldExclude("/project/.git/objects/abc"))
    }

    @Test
    fun `shouldExclude returns true for node_modules`() {
        assertTrue(ProjectUtils.shouldExclude("/project/node_modules/package/index.js"))
    }

    @Test
    fun `shouldExclude returns true for build directories`() {
        assertTrue(ProjectUtils.shouldExclude("/project/build/classes/Main.class"))
        assertTrue(ProjectUtils.shouldExclude("/project/out/production/file.class"))
        assertTrue(ProjectUtils.shouldExclude("/project/target/classes/Main.class"))
    }

    @Test
    fun `shouldExclude returns true for IDE directories`() {
        assertTrue(ProjectUtils.shouldExclude("/project/.idea/workspace.xml"))
        assertTrue(ProjectUtils.shouldExclude("/project/.gradle/caches/file"))
    }

    @Test
    fun `shouldExclude returns false for source files`() {
        assertFalse(ProjectUtils.shouldExclude("/project/src/main/kotlin/Main.kt"))
        assertFalse(ProjectUtils.shouldExclude("/project/src/App.java"))
        assertFalse(ProjectUtils.shouldExclude("/project/index.js"))
    }

    @Test
    fun `getProjectName returns null project gracefully`() {
        val name = ProjectUtils.getProjectName(null, null)
        assertEquals("Unknown", name)
    }
}
