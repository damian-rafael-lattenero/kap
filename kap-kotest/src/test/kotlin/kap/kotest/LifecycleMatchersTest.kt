package kap.kotest

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LifecycleMatchersTest {

    @Test
    fun `shouldHaveEvents passes on correct order`() {
        val tracker = LifecycleTracker()
        tracker.record("acquire")
        tracker.record("use")
        tracker.record("release")
        tracker.shouldHaveEvents("acquire", "use", "release")
    }

    @Test
    fun `shouldHaveEvents fails on wrong order`() {
        val tracker = LifecycleTracker()
        tracker.record("release")
        tracker.record("acquire")
        assertFailsWith<AssertionError> {
            tracker.shouldHaveEvents("acquire", "release")
        }
    }

    @Test
    fun `shouldHaveEvents fails on missing event`() {
        val tracker = LifecycleTracker()
        tracker.record("acquire")
        assertFailsWith<AssertionError> {
            tracker.shouldHaveEvents("acquire", "release")
        }
    }

    @Test
    fun `shouldHaveReleasedAfterUse passes`() {
        val tracker = LifecycleTracker()
        tracker.record("use-db")
        tracker.record("release-db")
        tracker.shouldHaveReleasedAfterUse("use-db", "release-db")
    }

    @Test
    fun `shouldHaveReleasedAfterUse fails on wrong order`() {
        val tracker = LifecycleTracker()
        tracker.record("release-db")
        tracker.record("use-db")
        assertFailsWith<AssertionError> {
            tracker.shouldHaveReleasedAfterUse("use-db", "release-db")
        }
    }

    @Test
    fun `shouldHaveEventCount passes`() {
        val tracker = LifecycleTracker()
        tracker.record("a")
        tracker.record("b")
        tracker.shouldHaveEventCount(2)
    }

    @Test
    fun `shouldContainEvent passes`() {
        val tracker = LifecycleTracker()
        tracker.record("important-event")
        tracker.shouldContainEvent("important-event")
    }
}
