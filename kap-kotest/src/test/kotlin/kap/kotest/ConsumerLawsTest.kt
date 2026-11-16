package kap.kotest

import kap.Backpressure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration

/**
 * Proves the reusable [consumerLaws] against a real bounded-queue mailbox —
 * exactly the semantics a downstream project (or a future `KapProcess`) must
 * provide — across every named strategy, and proves the FIFO law bites when an
 * implementation reorders.
 */
class ConsumerLawsTest {

    private val items = (1..20).toList()

    @Test
    fun `a suspend mailbox obeys the consumer laws`() = runTest {
        consumerLaws({ QueueMailbox<Int>(Backpressure.Suspend) }, items).verify()
    }

    @Test
    fun `a buffered mailbox obeys the consumer laws`() = runTest {
        consumerLaws({ QueueMailbox<Int>(Backpressure.Buffer(4)) }, items).verify()
    }

    @Test
    fun `a conflating mailbox obeys the consumer laws`() = runTest {
        consumerLaws({ QueueMailbox<Int>(Backpressure.Conflate) }, items).verify()
    }

    @Test
    fun `a drop-oldest mailbox obeys the consumer laws`() = runTest {
        consumerLaws({ QueueMailbox<Int>(Backpressure.DropOldest(3)) }, items).verify()
    }

    @Test
    fun `a drop-latest mailbox obeys the consumer laws`() = runTest {
        consumerLaws({ QueueMailbox<Int>(Backpressure.DropLatest(3)) }, items).verify()
    }

    @Test
    fun `the FIFO law catches a mailbox that reorders`() = runTest {
        assertFailsWith<AssertionError> {
            consumerLaws({ ReorderingMailbox(Backpressure.Buffer(4)) }, items).fifoForExactStrategies()
        }
    }
}

/**
 * A reference [MailboxUnderTest]: one mutex-guarded deque, one CONFLATED signal,
 * one consumer loop. The drop policy is applied at send time and every discarded
 * element is counted, so the drop-accounting law has a truthful oracle.
 */
private class QueueMailbox<A>(override val backpressure: Backpressure) : MailboxUnderTest<A> {

    private val capacity: Int = when (val strategy = backpressure) {
        Backpressure.Suspend, Backpressure.Conflate -> 1
        is Backpressure.Buffer -> strategy.capacity
        is Backpressure.DropOldest -> strategy.capacity
        is Backpressure.DropLatest -> strategy.capacity
        is Backpressure.Latest -> strategy.n
    }

    private val mutex = Mutex()
    private val queue = ArrayDeque<A>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    override var dropped: Long = 0L
        private set

    override suspend fun send(item: A) {
        if (backpressure.isExactStrategy()) {
            sendExact(item)
        } else {
            sendLossy(item)
        }
    }

    private suspend fun sendExact(item: A) {
        while (true) {
            val accepted = mutex.withLock {
                if (queue.size < capacity) {
                    queue.addLast(item)
                    signal.trySend(Unit)
                    true
                } else {
                    false
                }
            }
            if (accepted) return
            signal.receive() // the consumer took one; recheck
        }
    }

    private suspend fun sendLossy(item: A) {
        mutex.withLock {
            when (val strategy = backpressure) {
                Backpressure.Conflate -> {
                    if (queue.isNotEmpty()) {
                        queue.removeFirst()
                        dropped += 1
                    }
                    queue.addLast(item)
                }
                is Backpressure.DropOldest -> {
                    while (queue.size >= strategy.capacity) {
                        queue.removeFirst()
                        dropped += 1
                    }
                    queue.addLast(item)
                }
                is Backpressure.Latest -> {
                    while (queue.size >= strategy.n) {
                        queue.removeFirst()
                        dropped += 1
                    }
                    queue.addLast(item)
                }
                is Backpressure.DropLatest -> {
                    if (queue.size >= strategy.capacity) {
                        dropped += 1
                    } else {
                        queue.addLast(item)
                    }
                }
                Backpressure.Suspend, is Backpressure.Buffer -> error("exact strategy in the lossy path")
            }
            signal.trySend(Unit)
        }
    }

    override fun start(scope: CoroutineScope, handler: suspend (A) -> Unit, budget: Duration?): MailboxConsumer {
        val job = scope.launch {
            while (isActive) {
                val item = mutex.withLock {
                    queue.removeFirstOrNull().also { taken -> if (taken != null) signal.trySend(Unit) }
                }
                if (item == null) {
                    signal.receive()
                } else {
                    runItem(item, handler, budget)
                }
            }
        }
        return object : MailboxConsumer {
            override suspend fun stop() {
                job.cancelAndJoin()
            }
        }
    }

    private suspend fun runItem(item: A, handler: suspend (A) -> Unit, budget: Duration?) {
        try {
            if (budget == null) handler(item) else withTimeoutOrNull(budget) { handler(item) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // isolation is the law: one failing item never stops the consumer
        }
    }
}

/** Deliberately unlawful: delivers the buffered items in reverse order. */
private class ReorderingMailbox(override val backpressure: Backpressure) : MailboxUnderTest<Int> {

    private val sent = mutableListOf<Int>()

    override val dropped: Long = 0L

    override suspend fun send(item: Int) {
        sent += item
    }

    override fun start(scope: CoroutineScope, handler: suspend (Int) -> Unit, budget: Duration?): MailboxConsumer {
        val job = scope.launch {
            sent.toList().asReversed().forEach { handler(it) }
        }
        return object : MailboxConsumer {
            override suspend fun stop() {
                job.cancelAndJoin()
            }
        }
    }
}
