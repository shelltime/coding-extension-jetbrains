package xyz.shelltime.jetbrains.heartbeat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
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
    private var lastActivity: LastActivityState? = null
    private val pendingHeartbeats = ConcurrentLinkedQueue<HeartbeatData>()

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
     * Check if a heartbeat should be sent for this file
     */
    private fun shouldSendHeartbeat(
        filePath: String,
        isWrite: Boolean,
        lineNumber: Int?,
        cursorPosition: Int?
    ): Boolean {
        // Saves always trigger heartbeats
        if (isWrite) {
            return true
        }

        // Check for duplicate activity
        lastActivity?.let { last ->
            if (last.entity == filePath &&
                last.lineNumber == lineNumber &&
                last.cursorPosition == cursorPosition
            ) {
                logger.log("Skipping duplicate activity for $filePath")
                return false
            }
        }

        // Check debounce interval
        val now = System.currentTimeMillis()
        val lastTime = lastHeartbeat[filePath] ?: 0

        if (now - lastTime < Constants.DEBOUNCE_MS) {
            return false
        }

        // Update last activity state
        lastActivity = LastActivityState(filePath, lineNumber, cursorPosition)
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
        if (ProjectUtils.shouldExclude(file.path)) return false
        return true
    }

    /**
     * Handle document change events
     */
    private fun handleDocumentChange(event: DocumentEvent) {
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        if (!isValidFile(file)) return

        val editor = getActiveEditor()
        val lineNumber = editor?.let { it.caretModel.logicalPosition.line + 1 }
        val cursorPosition = editor?.caretModel?.logicalPosition?.column

        if (shouldSendHeartbeat(file.path, false, lineNumber, cursorPosition)) {
            val heartbeat = createHeartbeat(file, document, false, lineNumber, cursorPosition)
            addHeartbeat(heartbeat)
        }
    }

    /**
     * Handle caret/cursor change events
     */
    private fun handleCaretChange(event: CaretEvent) {
        val editor = event.editor
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        if (!isValidFile(file)) return

        val lineNumber = event.newPosition.line + 1
        val cursorPosition = event.newPosition.column

        if (shouldSendHeartbeat(file.path, false, lineNumber, cursorPosition)) {
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
        val editor = getActiveEditor()
        val lineNumber = editor?.let { it.caretModel.logicalPosition.line + 1 }
        val cursorPosition = editor?.caretModel?.logicalPosition?.column

        if (shouldSendHeartbeat(file.path, false, lineNumber, cursorPosition)) {
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
        val editor = getActiveEditor()
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
     * Get the active editor for the project
     */
    private fun getActiveEditor(): Editor? {
        return FileEditorManager.getInstance(project).selectedTextEditor
    }

    /**
     * Register listeners for an editor
     */
    fun registerEditor(editor: Editor) {
        editor.document.addDocumentListener(documentListener, this)
        editor.caretModel.addCaretListener(caretListener, this)
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
        lastHeartbeat.clear()
        pendingHeartbeats.clear()
        logger.log("Disposed heartbeat collector")
    }
}
