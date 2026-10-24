package xyz.shelltime.jetbrains

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import xyz.shelltime.jetbrains.heartbeat.HeartbeatPayload

/**
 * TEMPORARY, for CI only: which coroutine primitive hangs on the IDE's test runtime. The class
 * sorts first, so nothing else has run before it.
 */
@TestMethodOrder(MethodOrderer.MethodName::class)
class AaaCoroutineProbeTest {

    @Test
    @Timeout(5)
    fun `01 runBlocking that never suspends`() {
        runBlocking { }
    }

    @Test
    @Timeout(5)
    fun `02 suspension resumed on the same thread`() {
        runBlocking {
            val done = CompletableDeferred<Unit>()
            launch { done.complete(Unit) }
            done.await()
        }
    }

    @Test
    @Timeout(5)
    fun `03 async in runBlocking`() {
        runBlocking { async { 1 }.await() }
    }

    @Test
    @Timeout(5)
    fun `04 async suspended on a deferred the caller completes`() {
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val go = CompletableDeferred<Unit>()
            val task = async {
                started.complete(Unit)
                go.await()
            }
            started.await()
            go.complete(Unit)
            task.await()
        }
    }

    @Test
    @Timeout(5)
    fun `05 delay`() {
        runBlocking { delay(10) }
    }

    @Test
    @Timeout(5)
    fun `06 withContext Default`() {
        runBlocking { withContext(Dispatchers.Default) { } }
    }

    @Test
    @Timeout(5)
    fun `07 withContext IO`() {
        runBlocking { withContext(Dispatchers.IO) { } }
    }

    @Test
    @Timeout(5)
    fun `08 cancelAndJoin a job on Default`() {
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val job = CoroutineScope(Dispatchers.Default).launch {
                started.complete(Unit)
                awaitCancellation()
            }
            started.await()
            job.cancelAndJoin()
        }
    }

    @Test
    @Timeout(5)
    fun `09 kotlin-reflect through serializer`() {
        serializer<HeartbeatPayload>()
    }
}
