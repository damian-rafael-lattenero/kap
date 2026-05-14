import kap.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Two functions with SAME param types and SAME return type → the processor
// detects the collision and emits `kap{FunctionName}(::fn)` fallback instead
// of a plain `kap(::fn)` (which would clash as identical-signature overloads).
@KapTypeSafe
fun greet(name: String, age: Int): String = "Hello $name, you are $age"

@KapTypeSafe
fun farewell(name: String, age: Int): String = "Bye $name, you are $age"

class KapCollisionTest {

    @Test
    fun `same-signature functions get function-suffixed kap entries`() = runTest {
        val g = kapGreet(::greet)
            .with { name from "Alice" }
            .with { age from 30 }
            .evalGraph()

        val f = kapFarewell(::farewell)
            .with { name from "Alice" }
            .with { age from 30 }
            .evalGraph()

        assertEquals("Hello Alice, you are 30", g)
        assertEquals("Bye Alice, you are 30", f)
    }
}
