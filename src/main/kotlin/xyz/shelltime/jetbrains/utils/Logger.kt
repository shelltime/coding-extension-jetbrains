package xyz.shelltime.jetbrains.utils

import com.intellij.openapi.diagnostic.Logger as IdeaLogger

/**
 * Logger wrapper for debug logging
 */
class Logger(private val tag: String, private var debug: Boolean = false) {
    private val ideaLogger = IdeaLogger.getInstance("ShellTime.$tag")

    /**
     * Log a debug message (only if debug mode is enabled)
     */
    fun log(message: String) {
        if (debug) {
            ideaLogger.info("[$tag] $message")
        }
    }

    /**
     * Log a warning message (always logged)
     */
    fun warn(message: String) {
        ideaLogger.warn("[$tag] $message")
    }

    /**
     * Log an error message (always logged)
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            ideaLogger.error("[$tag] $message", throwable)
        } else {
            ideaLogger.error("[$tag] $message")
        }
    }

    /**
     * Enable or disable debug logging
     */
    fun setDebug(enabled: Boolean) {
        debug = enabled
    }
}
