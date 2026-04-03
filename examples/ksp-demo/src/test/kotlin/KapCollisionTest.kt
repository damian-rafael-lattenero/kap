import kap.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Two functions with SAME param types and SAME return type → kap() collision
@KapTypeSafe
fun greet(name: String, age: Int): String = "Hello $name, you are $age"

@KapTypeSafe
fun farewell(name: String, age: Int): String = "Bye $name, you are $age"

class KapCollisionTest {

    @Test
    fun `two functions with same signature both generate kap and kapFunctionName`() = runTest {
        val g = kap(Greet)
            .withName { "Alice" }
            .withAge { 30 }
            .executeGraph()

        val f = kap(Farewell)
            .withName { "Alice" }
            .withAge { 30 }
            .executeGraph()

        assertEquals("Hello Alice, you are 30", g)
        assertEquals("Bye Alice, you are 30", f)
    }
}
