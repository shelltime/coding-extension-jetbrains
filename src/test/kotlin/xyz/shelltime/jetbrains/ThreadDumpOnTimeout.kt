package xyz.shelltime.jetbrains

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adds every thread's stack, and the coroutines when the coroutine debug agent runs, to a test
 * that timed out: the timeout's own trace shows only the test thread, which is usually parked
 * waiting for another one. Add it to a test class with @ExtendWith.
 */
class ThreadDumpOnTimeout : TestExecutionExceptionHandler {
    override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
        if (throwable !is TimeoutException) throw throwable
        // Later timeouts usually wait on what the first ones left behind
        val details = if (dumps.incrementAndGet() <= MAX_DUMPS) {
            dump()
        } else {
            "(threads are dumped for the first $MAX_DUMPS timeouts)"
        }
        throw AssertionError("${throwable.message}\n$details", throwable)
    }

    private fun dump(): String = buildString {
        appendLine("JVM arguments: ${ManagementFactory.getRuntimeMXBean().inputArguments}")
        for (name in System.getProperties().stringPropertyNames().filter { it.startsWith("kotlinx.coroutines") }.sorted()) {
            appendLine("$name=${System.getProperty(name)}")
        }
        appendLine("kotlin-stdlib ${KotlinVersion.CURRENT}: ${codeSource("kotlin.KotlinVersion")}")
        appendLine("kotlinx-coroutines: ${codeSource("kotlinx.coroutines.Job")}")
        appendLine("kotlin-reflect: ${codeSource("kotlin.reflect.jvm.internal.KClassImpl")}")
        appendLine()
        for (info in ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            append("\"${info.threadName}\" #${info.threadId} ${info.threadState}")
            info.lockName?.let { append(" on $it") }
            info.lockOwnerName?.let { append(" owned by \"$it\"") }
            appendLine()
            info.stackTrace.forEachIndexed { depth, frame ->
                appendLine("    at $frame")
                info.lockedMonitors.filter { it.lockedStackDepth == depth }.forEach { appendLine("    - locked $it") }
            }
            info.lockedSynchronizers.forEach { appendLine("    - holds $it") }
            appendLine()
        }
        append(coroutineDump())
    }

    // The IDE's class loader leaves codeSource empty, so ask for the class file itself
    private fun codeSource(className: String): String = try {
        val type = Class.forName(className)
        type.getResource("${type.simpleName}.class")?.toString() ?: "unknown"
    } catch (e: Throwable) {
        "not loaded ($e)"
    }

    private fun coroutineDump(): String = try {
        val probes = Class.forName("kotlinx.coroutines.debug.internal.DebugProbesImpl")
        val out = ByteArrayOutputStream()
        probes.getMethod("dumpCoroutines", PrintStream::class.java)
            .invoke(probes.getField("INSTANCE").get(null), PrintStream(out, true))
        out.toString()
    } catch (e: Throwable) {
        "No coroutine dump: ${e.cause ?: e}"
    }

    companion object {
        private const val MAX_DUMPS = 3
        private val dumps = AtomicInteger()
    }
}
