package xyz.shelltime.jetbrains.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import xyz.shelltime.jetbrains.services.ShellTimeProjectService

/**
 * Listener for file editor events (open, close, switch)
 */
class FileEditorListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        project.service<ShellTimeProjectService>().onFileSwitch(file)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        event.newFile?.let { file ->
            project.service<ShellTimeProjectService>().onFileSwitch(file)
        }
    }
}

/**
 * Listener for file save events (application-level)
 */
class FileSaveListener : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = findProject(file) ?: return

        // Only the project the file belongs to records the save. Notifying every
        // open project produced one write heartbeat per project, each attributed
        // to that project.
        project.service<ShellTimeProjectService>().onFileSave(file)
    }

    private fun findProject(file: VirtualFile): Project? {
        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        return openProjects.firstOrNull { FileEditorManager.getInstance(it).isFileOpen(file) }
            ?: ProjectLocator.getInstance().guessProjectForFile(file)?.takeIf { !it.isDisposed }
    }
}
