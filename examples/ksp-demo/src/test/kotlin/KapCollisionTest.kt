import kap.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Two functions with SAME param types and SAME return type → kapDsl() uses marker objects to avoid collision
@KapTypeSafe
fun greet(name: String, age: Int): String = "Hello $name, you are $age"

@KapTypeSafe
fun farewell(name: String, age: Int): String = "Bye $name, you are $age"

class KapCollisionTest {

    @Test
    fun `two functions with same signature both generate kapDsl and kap{FunctionName}`() = runTest {
        val g = kapDsl(Greet)
            .withName { "Alice" }
            .withAge { 30 }
            .evalGraph()

        val f = kapDsl(Farewell)
            .withName { "Alice" }
            .withAge { 30 }
            .evalGraph()

        assertEquals("Hello Alice, you are 30", g)
        assertEquals("Bye Alice, you are 30", f)
    }
}
