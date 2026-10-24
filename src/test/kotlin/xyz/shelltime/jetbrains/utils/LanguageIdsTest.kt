package xyz.shelltime.jetbrains.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanguageIdsTest {

    @Test
    fun `file types map to the VS Code language ids`() {
        assertEquals("typescriptreact", LanguageIds.languageId("TypeScript JSX", "tsx", "App.tsx"))
        assertEquals("typescript", LanguageIds.languageId("TypeScript", "ts", "index.ts"))
        assertEquals("csharp", LanguageIds.languageId("C#", "cs", "Program.cs"))
        assertEquals("shellscript", LanguageIds.languageId("Shell Script", "sh", "run.sh"))
        assertEquals("plaintext", LanguageIds.languageId("PLAIN_TEXT", "txt", "notes.txt"))
        assertEquals("kotlin", LanguageIds.languageId("Kotlin", "kts", "build.gradle.kts"))
    }

    @Test
    fun `file types that don't say fall back to the extension`() {
        // TextMate highlighting in IDEs without the language plugin, CLion's C/C++
        assertEquals("python", LanguageIds.languageId("TextMate", "py", "main.py"))
        assertEquals("rust", LanguageIds.languageId("TextMate", "rs", "lib.rs"))
        assertEquals("cpp", LanguageIds.languageId("ObjectiveC", "hpp", "util.hpp"))
        assertEquals("c", LanguageIds.languageId("ObjectiveC", "h", "util.h"))
        assertEquals("groovy", LanguageIds.languageId("Gradle", "gradle", "build.gradle"))
        assertEquals("zig", LanguageIds.languageId("TextMate", "zig", "main.zig"))
    }

    @Test
    fun `files without an extension use their name`() {
        assertEquals("dockerfile", LanguageIds.languageId("Dockerfile", null, "Dockerfile"))
        assertEquals("makefile", LanguageIds.languageId("TextMate", null, "Makefile"))
        assertEquals("unknown file type", LanguageIds.languageId("Unknown File Type", null, "LICENSE"))
    }
}
