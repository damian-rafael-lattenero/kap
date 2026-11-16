package kap.kotest

import kap.Backpressure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The mailbox half of the consumer law run: a bounded mailbox with a **named**
 * strategy and **one** consumer loop. Implement it over your own mailbox (a
 * `Channel`-backed queue, a `KapProcess`-style actor, a hot signal) and hand a
 * fresh instance to the suite.
 *
 * ## Contract the laws hold you to
 *
 * - [backpressure] is the strategy this mailbox actually enforces.
 * - [send] suspends under exact strategies (`Suspend`, `Buffer(n)`) until the
 *   consumer can take the element; under lossy strategies it never suspends and
 *   applies the named drop policy.
 * - [dropped] counts **every** element the mailbox discarded — including the
 *   superseded/replaced ones — so `delivered + dropped == sent`.
 * - [start] launches the single consumer in [scope] (the test's virtual-time
 *   scope: never create your own dispatcher) and invokes [handler] one item at
 *   a time. A failing handler never stops the consumer: the next item runs.
 * - A non-null [budget] is the per-item budget: an item whose handler exceeds
 *   it is abandoned and the consumer moves on. The budget must not swallow the
 *   consumer's own cancellation.
 * - [MailboxConsumer.stop] cancels an in-flight handler, waits for the consumer
 *   to finish, and is idempotent.
 *
 * ```
 * class MyMailbox<A>(override val backpressure: Backpressure) : MailboxUnderTest<A> {
 *     override var dropped: Long = 0
 *     override suspend fun send(item: A) { ... }
 *     override fun start(scope: CoroutineScope, handler: suspend (A) -> Unit, budget: Duration?) = ...
 * }
 *
 * @Test
 * fun `my mailbox passes the consumer laws`() = runTest {
 *     consumerLaws({ MyMailbox<Int>(Backpressure.Buffer(4)) }, items = (1..20).toList()).verify()
 * }
 * ```
 */
interface MailboxUnderTest<A> {

    /** The named policy this mailbox enforces. */
    val backpressure: Backpressure

    /** Every element this mailbox discarded (superseded ones included). */
    val dropped: Long

    /** Offers one element: suspends under exact strategies, applies the named drop policy under lossy ones. */
    suspend fun send(item: A)

    /** Starts the single consumer in [scope]; [budget] is the optional per-item budget. */
    fun start(scope: CoroutineScope, handler: suspend (A) -> Unit, budget: Duration? = null): MailboxConsumer
}

/** The running consumer of a [MailboxUnderTest]. */
interface MailboxConsumer {
    /** Cancels an in-flight handler, waits for the consumer to finish. Idempotent. */
    suspend fun stop()
}

/**
 * Reusable laws of a bounded mailbox with one consumer, proven under virtual
 * time (no sleeps, no wall clock). Obtain one from
 * [`consumerLaws`][consumerLaws] inside `runTest`.
 *
 * 1. **At most one handler in flight.** Items never run concurrently, however
 *    the mailbox is implemented.
 * 2. **FIFO for exact strategies.** `Suspend` and `Buffer(n)` deliver every
 *    item in send order. (Lossy strategies are not asked to be FIFO for the
 *    items they drop; their survivors still keep order.)
 * 3. **A failing item never stops the next.** Handler exceptions are isolated:
 *    the consumer survives and processes the following item.
 * 4. **A per-item budget abandons and continues.** An item that exceeds its
 *    budget is abandoned and the next item runs.
 * 5. **Cancellation is never swallowed.** Stopping the consumer cancels the
 *    in-flight handler, `stop()` returns, and nothing is handled afterwards.
 * 6. **Drop accounting is honest.** With a stalled consumer,
 *    `delivered + dropped == sent` for lossy strategies, and exact strategies
 *    drop nothing.
 */
class ConsumerLaws<A> internal constructor(
    private val scope: TestScope,
    private val newMailbox: () -> MailboxUnderTest<A>,
    private val items: List<A>,
    private val itemBudget: Duration,
    private val stall: Duration,
) {

    /** Law 1 — a mailbox never runs two handlers at the same time. */
    suspend fun atMostOneHandlerInFlight() {
        val mailbox = newMailbox()
        var active = 0
        var maxActive = 0
        var handled = 0
        val consumer = mailbox.start(
            scope,
            handler = { _ ->
                active += 1
                maxActive = maxOf(maxActive, active)
                delay(HANDLER_DELAY)
                handled += 1
                active -= 1
            },
        )

        items.forEach { mailbox.send(it) }
        scope.testScheduler.advanceUntilIdle()
        consumer.stopOrFail()

        assertTrue(handled > 0, "${mailbox.backpressure}: the mailbox delivered nothing")
        assertEquals(1, maxActive, "${mailbox.backpressure}: $maxActive handlers ran at the same time")
    }

    /** Law 2 — exact strategies deliver every item in send order (FIFO). */
    suspend fun fifoForExactStrategies() {
        if (!newMailbox().backpressure.isExactStrategy()) return

        val mailbox = newMailbox()
        val handled = mutableListOf<A>()
        val consumer = mailbox.start(scope, handler = { handled += it })

        items.forEach { mailbox.send(it) }
        scope.testScheduler.advanceUntilIdle()
        consumer.stopOrFail()

        assertEquals(items, handled, "${mailbox.backpressure}: exact strategies must deliver every item in FIFO order")
    }

    /** Law 3 — a handler failure is isolated: the consumer processes the next item. */
    suspend fun aFailingItemNeverStopsTheNext() {
        val mailbox = newMailbox()
        val failing = items.first()
        val next = items[1]
        val handled = mutableListOf<A>()
        val consumer = mailbox.start(
            scope,
            handler = { item ->
                if (item == failing) error("the handler failed for $item")
                handled += item
            },
        )

        mailbox.send(failing)
        scope.testScheduler.advanceUntilIdle()
        mailbox.send(next)
        scope.testScheduler.advanceUntilIdle()
        consumer.stopOrFail()

        assertEquals(listOf(next), handled, "${mailbox.backpressure}: a failing item stopped the consumer")
    }

    /**
     * Law 4 — an item that exceeds its per-item budget is abandoned and the
     * consumer keeps going with the next item.
     */
    suspend fun perItemBudgetAbandonsAndContinues() {
        val mailbox = newMailbox()
        val slow = items.first()
        val next = items[1]
        val completed = mutableListOf<A>()
        val consumer = mailbox.start(
            scope,
            handler = { item ->
                if (item == slow) delay(stall)
                completed += item
            },
            budget = itemBudget,
        )

        mailbox.send(slow)
        scope.testScheduler.advanceUntilIdle()
        mailbox.send(next)
        scope.testScheduler.advanceUntilIdle()
        consumer.stopOrFail()

        assertEquals(
            listOf(next),
            completed,
            "${mailbox.backpressure}: the budget must abandon the slow item and keep the consumer alive",
        )
    }

    /** Law 5 — stopping the consumer cancels the in-flight handler and nothing runs afterwards. */
    suspend fun cancellationIsNeverSwallowed() {
        val mailbox = newMailbox()
        var invocations = 0
        var cancellationSeen = false
        val consumer = mailbox.start(
            scope,
            handler = { _ ->
                invocations += 1
                try {
                    delay(stall)
                } catch (e: CancellationException) {
                    cancellationSeen = true
                    throw e
                }
            },
            budget = itemBudget,
        )

        mailbox.send(items.first())
        scope.testScheduler.runCurrent()
        consumer.stopOrFail()

        assertTrue(cancellationSeen, "${mailbox.backpressure}: stop() did not cancel the in-flight handler")
        assertEquals(1, invocations, "${mailbox.backpressure}: the handler ran more than once")

        mailbox.send(items[1])
        scope.testScheduler.advanceUntilIdle()
        consumer.stopOrFail()

        assertEquals(1, invocations, "${mailbox.backpressure}: a stopped consumer handled another item")
    }

    /** Law 6 — delivered + dropped == sent for lossy strategies; exact strategies drop nothing. */
    suspend fun dropAccountingIsHonest() {
        val mailbox = newMailbox()
        var handled = 0
        val consumer = mailbox.start(
            scope,
            handler = {
                delay(stall)
                handled += 1
            },
        )

        items.forEach { mailbox.send(it) }
        scope.testScheduler.advanceUntilIdle()
        consumer.stopOrFail()

        if (mailbox.backpressure.isExactStrategy()) {
            assertEquals(0L, mailbox.dropped, "${mailbox.backpressure}: an exact strategy dropped elements")
            assertEquals(
                items.size.toLong(),
                handled.toLong(),
                "${mailbox.backpressure}: an exact strategy lost elements",
            )
        } else {
            assertEquals(
                items.size.toLong(),
                handled + mailbox.dropped,
                "${mailbox.backpressure}: delivered ($handled) + dropped (${mailbox.dropped}) != sent (${items.size})",
            )
        }
    }

    /** Runs all six laws. The one-line entry point downstream projects call. */
    suspend fun verify() {
        atMostOneHandlerInFlight()
        fifoForExactStrategies()
        aFailingItemNeverStopsTheNext()
        perItemBudgetAbandonsAndContinues()
        cancellationIsNeverSwallowed()
        dropAccountingIsHonest()
    }

    private suspend fun MailboxConsumer.stopOrFail() {
        val stopped = withTimeoutOrNull(STOP_TIMEOUT) { stop() }
        if (stopped == null) fail("the consumer did not stop within $STOP_TIMEOUT (a stop must cancel and join)")
    }

    private companion object {
        val HANDLER_DELAY = 10.milliseconds
        val STOP_TIMEOUT = 5.seconds
    }
}

/**
 * Builds the reusable consumer law suite for the mailbox [newMailbox] creates.
 * Call it inside `runTest`; the laws are virtual-time only.
 *
 * [items] must contain at least two **distinct** elements. [itemBudget] is the
 * per-item budget the budget law injects; it must be shorter than [stall] (the
 * slow handler's virtual delay) so the abandonment is observable.
 *
 * ```
 * @Test
 * fun `my mailbox obeys the consumer laws`() = runTest {
 *     consumerLaws({ MyMailbox<Int>(Backpressure.Buffer(4)) }, (1..20).toList()).verify()
 * }
 * ```
 */
fun <A> TestScope.consumerLaws(
    newMailbox: () -> MailboxUnderTest<A>,
    items: List<A>,
    itemBudget: Duration = 100.milliseconds,
    stall: Duration = 10.seconds,
): ConsumerLaws<A> {
    require(items.size >= 2) { "consumer laws need at least two elements" }
    require(items.distinct().size == items.size) { "the sample items must be distinct" }
    require(itemBudget < stall) { "the per-item budget ($itemBudget) must be shorter than the stall ($stall)" }
    return ConsumerLaws(this, newMailbox, items, itemBudget, stall)
}
