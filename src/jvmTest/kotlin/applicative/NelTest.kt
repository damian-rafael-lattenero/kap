package applicative

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NelTest {

    @Test
    fun `nel wraps single value`() {
        val nel = 42.nel()
        assertEquals(42, nel.head)
        assertEquals(emptyList(), nel.tail)
    }

    @Test
    fun `Nel of creates from varargs`() {
        val nel = Nel.of(1, 2, 3)
        assertEquals(1, nel.head)
        assertEquals(listOf(2, 3), nel.tail)
    }

    @Test
    fun `Nel of with single element`() {
        val nel = Nel.of("only")
        assertEquals("only", nel.head)
        assertEquals(emptyList(), nel.tail)
    }

    @Test
    fun `size returns 1 for single element`() {
        assertEquals(1, Nel(42).size)
    }

    @Test
    fun `size returns head plus tail`() {
        assertEquals(4, Nel.of(1, 2, 3, 4).size)
    }

    @Test
    fun `get index 0 returns head`() {
        val nel = Nel.of("a", "b", "c")
        assertEquals("a", nel[0])
    }

    @Test
    fun `get index gt 0 returns tail element`() {
        val nel = Nel.of("a", "b", "c")
        assertEquals("b", nel[1])
        assertEquals("c", nel[2])
    }

    @Test
    fun `isEmpty always returns false`() {
        assertFalse(Nel(1).isEmpty())
        assertFalse(Nel.of(1, 2, 3).isEmpty())
    }

    @Test
    fun `plus concatenates two Nels preserving order`() {
        val a = Nel.of(1, 2)
        val b = Nel.of(3, 4)
        val result = a + b
        assertEquals(listOf(1, 2, 3, 4), result.toList())
    }

    @Test
    fun `plus with single element Nels`() {
        val result = Nel(1) + Nel(2)
        assertEquals(listOf(1, 2), result.toList())
    }

    @Test
    fun `toString formats as Nel(elements)`() {
        assertEquals("Nel(1, 2, 3)", Nel.of(1, 2, 3).toString())
        assertEquals("Nel(42)", Nel(42).toString())
    }

    @Test
    fun `toList returns all elements in order`() {
        assertEquals(listOf("a", "b", "c"), Nel.of("a", "b", "c").toList())
    }

    @Test
    fun `can iterate with forEach`() {
        val collected = mutableListOf<Int>()
        Nel.of(10, 20, 30).forEach { collected.add(it) }
        assertEquals(listOf(10, 20, 30), collected)
    }
}
