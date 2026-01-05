package xyz.shelltime.jetbrains.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
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

    override fun beforeDocumentSaving(document: com.intellij.openapi.editor.Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        // Notify all open projects about the save
        com.intellij.openapi.project.ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                project.service<ShellTimeProjectService>().onFileSave(file)
            }
        }
    }
}
