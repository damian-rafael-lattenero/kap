package applicative

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComputationBuilderTest {

    @Test
    fun `bind executes computation and returns value`() = runTest {
        val result = Async {
            computation {
                val a = pure(1).bind()
                val b = pure(2).bind()
                a + b
            }
        }
        assertEquals(3, result)
    }

    @Test
    fun `bind is sequential — later steps depend on earlier values`() = runTest {
        val result = Async {
            computation {
                val x = pure(10).bind()
                val y = pure(x * 2).bind()  // depends on x
                val z = pure(y + 5).bind()  // depends on y
                z
            }
        }
        assertEquals(25, result)
    }

    @Test
    fun `bind propagates exceptions`() = runTest {
        val error = try {
            Async {
                computation {
                    val a = pure(1).bind()
                    val b = Computation<Int> { throw IllegalStateException("boom") }.bind()
                    a + b
                }
            }
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertEquals("boom", error?.message)
    }

    @Test
    fun `computation composes with ap — sequential then parallel`() = runTest {
        val result = Async {
            computation {
                val userId = pure("user-1").bind()
                // Now use the value in a parallel phase via bind
                val dashboard = (lift2 { name: String, cart: String -> "$name|$cart" }
                    .ap { "Name-$userId" }
                    .ap { "Cart-$userId" }).bind()
                dashboard
            }
        }
        assertEquals("Name-user-1|Cart-user-1", result)
    }

    @Test
    fun `computation works with flatMap interop`() = runTest {
        val result = Async {
            pure(5).flatMap { x ->
                computation {
                    val y = pure(x * 3).bind()
                    y + 1
                }
            }
        }
        assertEquals(16, result)
    }

    @Test
    fun `empty computation returns value directly`() = runTest {
        val result = Async {
            computation { 42 }
        }
        assertEquals(42, result)
    }

    @Test
    fun `bind lambda shorthand — executes suspend block directly`() = runTest {
        val result = Async {
            computation {
                val x = bind { 10 }
                val y = bind { x * 2 }  // depends on x
                val z = bind { y + 5 }  // depends on y
                z
            }
        }
        assertEquals(25, result)
    }

    @Test
    fun `bind lambda shorthand — value-dependent chain`() = runTest {
        val result = Async {
            computation {
                val user = bind { "Alice" }
                val greeting = bind { "Hello, $user!" }  // depends on user
                greeting
            }
        }
        assertEquals("Hello, Alice!", result)
    }
}
