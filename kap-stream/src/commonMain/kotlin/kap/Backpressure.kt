package kap

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * A **named** backpressure policy — the value that answers "what happens to an
 * element the consumer cannot take yet?". Every [KapStream] stage takes one;
 * the default is [Suspend], which is exact (nothing is ever dropped).
 *
 * | Strategy | Buffer | Drops | Never drops |
 * |---|---|---|---|
 * | [Suspend] | none | nothing | every element, exact order |
 * | [Buffer]`(n)` | `n` slots | nothing (the producer waits) | every element, exact order |
 * | [Conflate] | 1 slot, keep latest | every superseded element | the element buffered at completion |
 * | [DropOldest]`(n)` | `n` slots | the oldest buffered element on overflow | the most recent element |
 * | [DropLatest]`(n)` | `n` slots | the incoming element on overflow | the buffered (older) elements |
 * | [Latest]`(n)` | `n` slots, keep latest | everything older than the last `n` | the last `n` elements |
 *
 * All strategies preserve the upstream order among the elements they keep;
 * none reorders. Elements are never duplicated.
 */
sealed interface Backpressure {
    /** Exact: the producer suspends until the consumer takes the element. Nothing is dropped. */
    data object Suspend : Backpressure

    /**
     * Exact with decoupling: up to [capacity] elements wait for the consumer,
     * and the producer suspends only when the buffer is full. Nothing is
     * dropped.
     */
    data class Buffer(val capacity: Int = Channel.BUFFERED) : Backpressure {
        init {
            require(capacity >= 1) { "buffer capacity must be >= 1, was $capacity (use Suspend for 0)" }
        }
    }

    /**
     * Keeps only the most recent element: while the consumer is busy, every
     * new element replaces the previous one. Same value as [Latest]`(1)`.
     */
    data object Conflate : Backpressure

    /**
     * A [capacity]-slot buffer that drops the **oldest** buffered element when
     * a new one arrives and the buffer is full — the producer never waits, and
     * the freshest data survives.
     */
    data class DropOldest(val capacity: Int = 1) : Backpressure {
        init {
            require(capacity >= 1) { "dropOldest capacity must be >= 1, was $capacity" }
        }
    }

    /**
     * A [capacity]-slot buffer that drops the **incoming** element when the
     * buffer is full — the producer never waits, and the oldest data survives.
     */
    data class DropLatest(val capacity: Int = 1) : Backpressure {
        init {
            require(capacity >= 1) { "dropLatest capacity must be >= 1, was $capacity" }
        }
    }

    /** Keeps the last [n] elements: everything older is dropped when a new element arrives. */
    data class Latest(val n: Int = 1) : Backpressure {
        init {
            require(n >= 1) { "latest needs n >= 1, was $n" }
        }
    }
}

/**
 * Applies [strategy] to this flow as a real channel boundary (a producer
 * coroutine feeding a channel with the named capacity and overflow policy), so
 * the drop/keep semantics are exactly the documented ones and are not subject
 * to flow operator fusion.
 */
internal fun <A> Flow<A>.withBackpressure(strategy: Backpressure): Flow<A> = when (strategy) {
    Backpressure.Suspend -> this
    is Backpressure.Buffer -> decoupled(strategy.capacity, BufferOverflow.SUSPEND)
    Backpressure.Conflate -> decoupled(1, BufferOverflow.DROP_OLDEST)
    is Backpressure.DropOldest -> decoupled(strategy.capacity, BufferOverflow.DROP_OLDEST)
    is Backpressure.DropLatest -> decoupled(strategy.capacity, BufferOverflow.DROP_LATEST)
    is Backpressure.Latest -> decoupled(strategy.n, BufferOverflow.DROP_OLDEST)
}

private fun <A> Flow<A>.decoupled(capacity: Int, overflow: BufferOverflow): Flow<A> = flow {
    coroutineScope {
        val channel = Channel<A>(capacity, overflow)
        val producer = launch {
            this@decoupled.collect { channel.send(it) }
            channel.close()
        }
        try {
            for (element in channel) emit(element)
        } finally {
            producer.cancel()   // downstream left early: cancel the producer, never abandon it
        }
    }
}
