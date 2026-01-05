package xyz.shelltime.jetbrains.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Project-related utility functions
 */
object ProjectUtils {
    /**
     * Get the project name (last 2 path components)
     *
     * @param project The current project
     * @param file The current file
     * @return The project name
     */
    fun getProjectName(project: Project?, file: VirtualFile?): String {
        // Try to get from project first
        project?.basePath?.let { basePath ->
            return extractLastPathComponents(basePath, 2)
        }

        // Fall back to file's parent directories
        file?.let { f ->
            val parent = f.parent?.path ?: return f.name
            return extractLastPathComponents(parent, 2)
        }

        return "Unknown"
    }

    /**
     * Get the project root path
     *
     * @param project The current project
     * @param file The current file (fallback)
     * @return The project root path
     */
    fun getProjectRootPath(project: Project?, file: VirtualFile?): String {
        // Try project base path first
        project?.basePath?.let { return it }

        // Fall back to file's parent directory
        file?.parent?.path?.let { return it }

        return ""
    }

    /**
     * Get the language ID for a file
     *
     * @param file The virtual file
     * @return The language identifier
     */
    fun getLanguage(file: VirtualFile?): String {
        if (file == null) return "unknown"

        // Try to get from file type
        val fileType = file.fileType
        val typeName = fileType.name.lowercase()

        // Map common file types to language IDs
        return when (typeName) {
            "kotlin" -> "kotlin"
            "java" -> "java"
            "javascript" -> "javascript"
            "typescript" -> "typescript"
            "python" -> "python"
            "go" -> "go"
            "rust" -> "rust"
            "c" -> "c"
            "c++" -> "cpp"
            "ruby" -> "ruby"
            "php" -> "php"
            "swift" -> "swift"
            "scala" -> "scala"
            "html" -> "html"
            "css" -> "css"
            "xml" -> "xml"
            "json" -> "json"
            "yaml" -> "yaml"
            "markdown" -> "markdown"
            "sql" -> "sql"
            "shell script" -> "shellscript"
            "plain_text" -> "plaintext"
            else -> {
                // Try to infer from extension
                file.extension?.lowercase() ?: typeName
            }
        }
    }

    /**
     * Extract the last N path components
     */
    private fun extractLastPathComponents(path: String, count: Int): String {
        val parts = path.split(File.separator, "/")
            .filter { it.isNotEmpty() }

        return if (parts.size >= count) {
            parts.takeLast(count).joinToString("/")
        } else {
            parts.joinToString("/")
        }
    }

    /**
     * Check if a file should be excluded from tracking
     *
     * @param filePath The file path to check
     * @return True if the file should be excluded
     */
    fun shouldExclude(filePath: String): Boolean {
        // Exclude .git directory
        if (filePath.contains("/.git/") || filePath.contains("\\.git\\")) {
            return true
        }

        // Exclude common non-code paths
        val excludePatterns = listOf(
            "/.idea/",
            "/build/",
            "/out/",
            "/target/",
            "/node_modules/",
            "/.gradle/",
            "/vendor/",
            "/__pycache__/"
        )

        for (pattern in excludePatterns) {
            if (filePath.contains(pattern)) {
                return true
            }
        }

        return false
    }
}
