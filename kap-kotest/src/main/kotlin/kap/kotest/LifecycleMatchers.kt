package kap.kotest

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Helper for tracking and asserting resource lifecycle events.
 *
 * ```kotlin
 * val lifecycle = LifecycleTracker()
 *
 * bracket(
 *     acquire = { lifecycle.record("acquire-db"); openDb() },
 *     use = { conn -> lifecycle.record("use-db"); Kap { conn.query() } },
 *     release = { conn -> lifecycle.record("release-db"); conn.close() }
 * )
 *
 * lifecycle.shouldHaveEvents("acquire-db", "use-db", "release-db")
 * lifecycle.shouldHaveReleasedAfterUse("use-db", "release-db")
 * ```
 */
class LifecycleTracker {
    private val _events = CopyOnWriteArrayList<String>()
    val events: List<String> get() = _events.toList()

    fun record(event: String) {
        _events.add(event)
    }

    /**
     * Assert that all expected events occurred in the given order.
     */
    fun shouldHaveEvents(vararg expected: String) {
        val actual = _events.toList()
        expected.forEach { event ->
            assert(event in actual) {
                "Expected event '$event' but events were: $actual"
            }
        }
        // Verify order
        var lastIndex = -1
        expected.forEach { event ->
            val index = actual.indexOf(event)
            assert(index > lastIndex) {
                "Expected '$event' after index $lastIndex but found at $index. Events: $actual"
            }
            lastIndex = index
        }
    }

    /**
     * Assert that a release event occurred after a use event.
     */
    fun shouldHaveReleasedAfterUse(useEvent: String, releaseEvent: String) {
        val actual = _events.toList()
        val useIndex = actual.indexOf(useEvent)
        val releaseIndex = actual.indexOf(releaseEvent)
        assert(useIndex >= 0) { "Use event '$useEvent' not found. Events: $actual" }
        assert(releaseIndex >= 0) { "Release event '$releaseEvent' not found. Events: $actual" }
        assert(releaseIndex > useIndex) {
            "Expected '$releaseEvent' (index $releaseIndex) after '$useEvent' (index $useIndex). Events: $actual"
        }
    }

    /**
     * Assert that the event count matches.
     */
    fun shouldHaveEventCount(expected: Int) {
        assert(_events.size == expected) {
            "Expected $expected events but got ${_events.size}: $_events"
        }
    }

    /**
     * Assert that a specific event occurred.
     */
    fun shouldContainEvent(event: String) {
        assert(event in _events) {
            "Expected event '$event' but events were: $_events"
        }
    }

    fun clear() = _events.clear()
}
