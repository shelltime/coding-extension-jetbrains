package xyz.shelltime.jetbrains.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.repo.GitRepositoryManager

/**
 * Git-related utility functions
 */
object GitUtils {
    /**
     * Get the current Git branch for a file
     *
     * @param project The current project
     * @param file The virtual file to check
     * @return The branch name, or empty string if not in a git repo
     */
    fun getGitBranch(project: Project?, file: VirtualFile?): String {
        if (project == null || file == null) {
            return ""
        }

        return try {
            // The quick lookup only reads the repository cache. getRepositoryForFile may update
            // it synchronously, which the platform reports as an error on the EDT.
            val repositoryManager = GitRepositoryManager.getInstance(project)
            val repository = repositoryManager.getRepositoryForFileQuick(file)
            repository?.currentBranch?.name ?: ""
        } catch (e: Exception) {
            ""
        } catch (e: NoClassDefFoundError) {
            // Git4Idea plugin may not be available
            ""
        }
    }

    /**
     * Get the root of the Git repository that contains a file
     *
     * @return The repository root path, or null when the file isn't in a known repository
     */
    fun getRepositoryRoot(project: Project?, file: VirtualFile?): String? {
        if (project == null || file == null) {
            return null
        }

        return try {
            GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file)?.root?.path
        } catch (e: Exception) {
            null
        } catch (e: NoClassDefFoundError) {
            null
        }
    }

    /**
     * Get the current Git branch for a file path
     *
     * @param project The current project
     * @param filePath The file path to check
     * @return The branch name, or empty string if not in a git repo
     */
    fun getGitBranch(project: Project?, filePath: String): String {
        if (project == null || filePath.isEmpty()) {
            return ""
        }

        return try {
            val vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            val file = vfs.findFileByPath(filePath)
            getGitBranch(project, file)
        } catch (e: Exception) {
            ""
        }
    }
}
