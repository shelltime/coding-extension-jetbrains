package xyz.shelltime.jetbrains.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Project-related utility functions
 */
object ProjectUtils {
    /**
     * Get the project name (last 2 path components of the project root)
     *
     * @param project The current project
     * @param file The current file
     * @return The project name
     */
    fun getProjectName(project: Project?, file: VirtualFile?): String {
        val root = getProjectRootPath(project, file)
        if (root.isEmpty()) {
            return file?.name ?: "Unknown"
        }
        return extractLastPathComponents(root, 2)
    }

    /**
     * Get the project root path: the project's base path for its own files; for a file
     * outside it (another attached root, a file opened from elsewhere) the Git repository
     * it is in, else its directory, as the other editors report it
     *
     * @param project The current project
     * @param file The current file (fallback)
     * @return The project root path
     */
    fun getProjectRootPath(project: Project?, file: VirtualFile?): String {
        val basePath = project?.basePath
        if (basePath != null && (file == null || isUnder(file.path, basePath))) {
            return basePath
        }

        GitUtils.getRepositoryRoot(project, file)?.let { return it }

        // Fall back to file's parent directory
        file?.parent?.path?.let { return it }

        return basePath ?: ""
    }

    private fun isUnder(path: String, root: String): Boolean {
        val normalizedPath = path.replace('\\', '/')
        val normalizedRoot = root.replace('\\', '/').trimEnd('/')
        return normalizedPath == normalizedRoot || normalizedPath.startsWith("$normalizedRoot/")
    }

    /**
     * Get the language ID for a file
     *
     * @param file The virtual file
     * @return The language identifier
     */
    fun getLanguage(file: VirtualFile?): String {
        if (file == null) return "unknown"
        return LanguageIds.languageId(file.fileType.name, file.extension, file.name)
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
     * Patterns are matched against the path inside the project, so a project that
     * itself lives under a directory like `build/` or `out/` is still tracked.
     *
     * @param filePath The file path to check
     * @param projectBasePath The project root, if known
     * @return True if the file should be excluded
     */
    fun shouldExclude(filePath: String, projectBasePath: String? = null): Boolean {
        val normalizedPath = filePath.replace('\\', '/')
        val basePath = projectBasePath?.replace('\\', '/')?.trimEnd('/')
        val path = if (!basePath.isNullOrEmpty() && normalizedPath.startsWith("$basePath/")) {
            // Keep the leading slash so top-level directories still match
            normalizedPath.substring(basePath.length)
        } else {
            normalizedPath
        }

        // Exclude .git directory
        if (path.contains("/.git/")) {
            return true
        }

        // Exclude paths that never hold the user's sources. Build output (build/, out/,
        // target/) is left to the project's own excluded folders: a package can be named
        // build too.
        val excludePatterns = listOf(
            "/.idea/",
            "/node_modules/",
            "/.gradle/",
            "/__pycache__/"
        )

        for (pattern in excludePatterns) {
            if (path.contains(pattern)) {
                return true
            }
        }

        return false
    }
}
