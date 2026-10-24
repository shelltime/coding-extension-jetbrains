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
    fun `shouldExclude leaves build directory names to the project's excluded folders`() {
        // Real sources live in packages named build, out, target or vendor
        assertFalse(ProjectUtils.shouldExclude("/project/src/main/kotlin/com/acme/build/Foo.kt", "/project"))
        assertFalse(ProjectUtils.shouldExclude("/project/internal/build/build.go", "/project"))
        assertFalse(ProjectUtils.shouldExclude("/project/vendor/lib/lib.go", "/project"))
        assertFalse(ProjectUtils.shouldExclude("/project/target/classes/Main.class", "/project"))
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
    fun `shouldExclude ignores excluded directory names above the project root`() {
        assertFalse(ProjectUtils.shouldExclude("/home/ci/build/repo/src/Main.kt", "/home/ci/build/repo"))
        assertFalse(ProjectUtils.shouldExclude("/Users/me/out/app/index.js", "/Users/me/out/app/"))
        assertFalse(ProjectUtils.shouldExclude("/go/src/vendor/tool/main.go", "/go/src/vendor/tool"))
    }

    @Test
    fun `shouldExclude still excludes directories inside the project`() {
        assertTrue(ProjectUtils.shouldExclude("/home/ci/build/repo/.idea/workspace.xml", "/home/ci/build/repo"))
        assertTrue(ProjectUtils.shouldExclude("/home/ci/build/repo/.git/config", "/home/ci/build/repo"))
        assertTrue(ProjectUtils.shouldExclude("/app/web/node_modules/pkg/index.js", "/app"))
    }

    @Test
    fun `shouldExclude checks the full path for files outside the project`() {
        assertTrue(ProjectUtils.shouldExclude("/other/node_modules/pkg/index.js", "/app"))
        assertTrue(ProjectUtils.shouldExclude("/application/.gradle/caches/file", "/app"))
    }

    @Test
    fun `shouldExclude handles Windows separators`() {
        assertTrue(ProjectUtils.shouldExclude("C:\\project\\.git\\config"))
        assertFalse(ProjectUtils.shouldExclude("C:\\build\\project\\src\\Main.kt", "C:\\build\\project"))
    }

    @Test
    fun `getProjectName returns null project gracefully`() {
        val name = ProjectUtils.getProjectName(null, null)
        assertEquals("Unknown", name)
    }
}
