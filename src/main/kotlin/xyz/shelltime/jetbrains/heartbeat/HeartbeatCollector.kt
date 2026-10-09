package xyz.shelltime.jetbrains.heartbeat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import xyz.shelltime.jetbrains.config.Constants
import xyz.shelltime.jetbrains.utils.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Tracks last activity state to prevent duplicate heartbeats
 */
private data class LastActivityState(
    val entity: String,
    val lineNumber: Int?,
    val cursorPosition: Int?
)

/**
 * Collects heartbeats from IDE events with debouncing
 */
class HeartbeatCollector(
    private val project: Project,
    private val pluginVersion: String,
    debug: Boolean = false
) : Disposable {
    private val logger = Logger("Collector", debug)
    private val lastHeartbeat = ConcurrentHashMap<String, Long>()
    @Volatile
    private var lastActivity: LastActivityState? = null
    private val pendingHeartbeats = ConcurrentLinkedQueue<HeartbeatData>()

    /** Owns the editor listeners while collecting; null when stopped */
    private var listenerDisposable: Disposable? = null

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            handleDocumentChange(event)
        }
    }

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            handleCaretChange(event)
        }
    }

    /**
     * Start listening to edits and cursor moves in this project's editors.
     *
     * The listeners are added to the editor event multicaster, so they also cover
     * editors that were opened before this collector existed (restored tabs).
     *
     * @param parentDisposable Disposed together with the listeners
     */
    fun start(parentDisposable: Disposable) {
        if (listenerDisposable != null) return

        val disposable = Disposer.newDisposable(parentDisposable, "ShellTimeHeartbeatListeners")
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addDocumentListener(documentListener, disposable)
        multicaster.addCaretListener(caretListener, disposable)
        listenerDisposable = disposable

        logger.log("Started collecting heartbeats")
    }

    /**
     * Stop listening to editor events. Already queued heartbeats are kept.
     */
    fun stop() {
        val disposable = listenerDisposable ?: return
        listenerDisposable = null
        Disposer.dispose(disposable)
        logger.log("Stopped collecting heartbeats")
    }

    /**
     * Navigation events (caret moves, file switches) can repeat without the user
     * doing anything, so they are dropped when the file and cursor are unchanged.
     * Edits always count, even when they leave the caret in place.
     */
    private fun isDuplicateNavigation(filePath: String, lineNumber: Int?, cursorPosition: Int?): Boolean {
        val current = LastActivityState(filePath, lineNumber, cursorPosition)
        val isDuplicate = lastActivity == current
        lastActivity = current

        if (isDuplicate) {
            logger.log("Skipping duplicate activity for $filePath")
        }
        return isDuplicate
    }

    /**
     * Check if a heartbeat should be sent for this file
     */
    private fun shouldSendHeartbeat(filePath: String, isWrite: Boolean): Boolean {
        // Saves always trigger heartbeats
        if (isWrite) {
            return true
        }

        // Check debounce interval
        val now = System.currentTimeMillis()
        val lastTime = lastHeartbeat[filePath] ?: 0

        if (now - lastTime < Constants.DEBOUNCE_MS) {
            return false
        }

        lastHeartbeat[filePath] = now
        return true
    }

    /**
     * Create a heartbeat from the current state
     */
    private fun createHeartbeat(
        file: VirtualFile,
        document: Document,
        isWrite: Boolean,
        lineNumber: Int?,
        cursorPosition: Int?
    ): HeartbeatData {
        val filePath = file.path
        val isDebugging = try {
            XDebuggerManager.getInstance(project).currentSession != null
        } catch (e: Exception) {
            false
        }

        return HeartbeatData(
            heartbeatId = SystemUtils.generateUUID(),
            entity = filePath,
            entityType = EntityType.file.name,
            category = if (isDebugging) Category.debugging.name else Category.coding.name,
            time = System.currentTimeMillis() / 1000,
            project = ProjectUtils.getProjectName(project, file),
            projectRootPath = ProjectUtils.getProjectRootPath(project, file),
            branch = GitUtils.getGitBranch(project, file),
            language = ProjectUtils.getLanguage(file),
            lines = document.lineCount,
            lineNumber = lineNumber,
            cursorPosition = cursorPosition,
            editor = Constants.EDITOR_ID,
            editorVersion = ApplicationInfo.getInstance().fullVersion,
            plugin = Constants.PLUGIN_ID,
            pluginVersion = pluginVersion,
            machine = SystemUtils.getMachineName(),
            os = SystemUtils.getOSName(),
            osVersion = SystemUtils.getOSVersion(),
            isWrite = isWrite
        )
    }

    /**
     * Add a heartbeat to the queue
     */
    private fun addHeartbeat(heartbeat: HeartbeatData) {
        pendingHeartbeats.add(heartbeat)
        logger.log("Queued heartbeat for ${heartbeat.entity} (isWrite: ${heartbeat.isWrite})")
    }

    /**
     * Check if a file is valid for tracking
     */
    private fun isValidFile(file: VirtualFile?): Boolean {
        if (file == null) return false
        if (!file.isInLocalFileSystem) return false
        if (ProjectUtils.shouldExclude(file.path, project.basePath)) return false
        return true
    }

    /**
     * Find an editor of this project that shows the document, preferring the selected one
     */
    private fun findEditor(document: Document): Editor? {
        val selected = FileEditorManager.getInstance(project).selectedTextEditor
        if (selected != null && selected.document == document) {
            return selected
        }
        return EditorFactory.getInstance().getEditors(document, project).firstOrNull()
    }

    /**
     * Handle document change events
     */
    private fun handleDocumentChange(event: DocumentEvent) {
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        if (!isValidFile(file)) return

        // The multicaster reports changes to every document in the IDE; only count
        // documents that are open in an editor of this project.
        val editor = findEditor(document) ?: return
        val lineNumber = editor.caretModel.logicalPosition.line + 1
        val cursorPosition = editor.caretModel.logicalPosition.column

        lastActivity = LastActivityState(file.path, lineNumber, cursorPosition)

        if (shouldSendHeartbeat(file.path, false)) {
            val heartbeat = createHeartbeat(file, document, false, lineNumber, cursorPosition)
            addHeartbeat(heartbeat)
        }
    }

    /**
     * Handle caret/cursor change events
     */
    private fun handleCaretChange(event: CaretEvent) {
        val editor = event.editor
        if (editor.project != project) return

        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        if (!isValidFile(file)) return

        val lineNumber = event.newPosition.line + 1
        val cursorPosition = event.newPosition.column

        if (isDuplicateNavigation(file.path, lineNumber, cursorPosition)) return

        if (shouldSendHeartbeat(file.path, false)) {
            val heartbeat = createHeartbeat(file, document, false, lineNumber, cursorPosition)
            addHeartbeat(heartbeat)
        }
    }

    /**
     * Handle file switch events
     */
    fun handleFileSwitch(file: VirtualFile?) {
        if (!isValidFile(file)) return

        val document = FileDocumentManager.getInstance().getDocument(file!!) ?: return
        val editor = findEditor(document)
        val lineNumber = editor?.let { it.caretModel.logicalPosition.line + 1 }
        val cursorPosition = editor?.caretModel?.logicalPosition?.column

        if (isDuplicateNavigation(file.path, lineNumber, cursorPosition)) return

        if (shouldSendHeartbeat(file.path, false)) {
            val heartbeat = createHeartbeat(file, document, false, lineNumber, cursorPosition)
            addHeartbeat(heartbeat)
        }
    }

    /**
     * Handle file save events
     */
    fun handleFileSave(file: VirtualFile?) {
        if (!isValidFile(file)) return

        val document = FileDocumentManager.getInstance().getDocument(file!!) ?: return
        val editor = findEditor(document)
        val lineNumber = editor?.let { it.caretModel.logicalPosition.line + 1 }
        val cursorPosition = editor?.caretModel?.logicalPosition?.column

        // Saves always bypass debouncing
        val heartbeat = createHeartbeat(file, document, true, lineNumber, cursorPosition)
        addHeartbeat(heartbeat)

        // Update last activity to prevent duplicate non-write events after save
        lastActivity = LastActivityState(file.path, lineNumber, cursorPosition)
        lastHeartbeat[file.path] = System.currentTimeMillis()
    }

    /**
     * Flush pending heartbeats
     *
     * @return List of pending heartbeats, clearing the queue
     */
    fun flush(): List<HeartbeatData> {
        val heartbeats = mutableListOf<HeartbeatData>()
        while (true) {
            val heartbeat = pendingHeartbeats.poll() ?: break
            heartbeats.add(heartbeat)
        }
        logger.log("Flushed ${heartbeats.size} heartbeats")
        return heartbeats
    }

    /**
     * Get the number of pending heartbeats
     */
    fun getPendingCount(): Int = pendingHeartbeats.size

    /**
     * Set debug logging
     */
    fun setDebug(enabled: Boolean) {
        logger.setDebug(enabled)
    }

    override fun dispose() {
        stop()
        lastHeartbeat.clear()
        pendingHeartbeats.clear()
        logger.log("Disposed heartbeat collector")
    }
}
