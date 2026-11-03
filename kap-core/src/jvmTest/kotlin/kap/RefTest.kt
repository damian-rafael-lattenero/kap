package kap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class RefTest {

    @Test
    fun `get set update and modify follow their contracts`() = runTest {
        val ref = Ref.of(1).evalGraph()

        assertEquals(1, ref.get().evalGraph())

        ref.set(5).evalGraph()
        assertEquals(5, ref.get().evalGraph())

        assertEquals(6, ref.update { it + 1 }.evalGraph(), "update returns the new value")
        assertEquals(6, ref.get().evalGraph())

        val label = ref.modify { current -> (current * 2) to "was $current" }.evalGraph()
        assertEquals("was 6", label, "modify returns the second component")
        assertEquals(12, ref.get().evalGraph(), "modify stores the first component")
    }

    @Test
    fun `every execution of Ref of creates a fresh reference`() = runTest {
        val refs = Ref.of(0)

        val first = refs.evalGraph()
        first.set(99).evalGraph()
        assertEquals(99, first.get().evalGraph())

        val second = refs.evalGraph()
        assertNotSame(first, second)
        assertEquals(0, second.get().evalGraph())
    }

    @Test
    fun `1000 concurrent increments lose no updates`() = runTest {
        val ref = Ref.of(0).evalGraph()

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(1_000) {
                    launch { ref.update { it + 1 }.evalGraph() }
                }
            }
        }

        assertEquals(1_000, ref.get().evalGraph())
    }

    @Test
    fun `cancellation of a caller waiting for the lock does not corrupt state`() = runTest {
        val ref = Ref.of(0).evalGraph()

        ref.mutex.lock()
        val waiter = launch { ref.update { it + 100 }.evalGraph() }
        yield() // let the waiter suspend on the mutex
        waiter.cancelAndJoin()
        ref.mutex.unlock()

        assertEquals(0, ref.get().evalGraph(), "the cancelled update must not be applied")
        assertEquals(1, ref.update { it + 1 }.evalGraph(), "the ref stays usable")
        assertEquals(1, ref.get().evalGraph())
    }
}
