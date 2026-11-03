package kap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The `.with` deprecation guards (issue 19): common mistakes on a `.with`
 * chain keep failing to compile, but with an explanatory message instead of
 * `None of the following candidates is applicable … Kap<uninferred A>`.
 *
 * Verified against the real diagnostics while designing the guards; this
 * suite pins the surface: both guards exist (facade classes KapKt /
 * WithGuardsKt) and are deprecated at ERROR level with the intended text.
 */
class WithDeprecationGuardTest {

    private val jvmFacade = Class.forName("kap.KapKt")
    private val guardsFacade = Class.forName("kap.WithGuardsKt")

    /** The plain-value guard: the only `with(Kap, Object)` erasure on KapKt. */
    private fun plainValueGuard(): java.lang.reflect.Method =
        jvmFacade.methods
            .filter { it.name == "with" }
            .single { it.parameterTypes.size == 2 && it.parameterTypes[1] == Object::class.java }
            ?: error("missing the plain-value .with guard")

    private fun catchAllGuard(): java.lang.reflect.Method =
        guardsFacade.methods.firstOrNull { it.name == "with" }
            ?: error("missing the catch-all .with guard")

    @Test
    fun `plain-value guard exists and is deprecated at ERROR level`() {
        val deprecation = assertNotNull(plainValueGuard().getAnnotation(Deprecated::class.java))
        assertEquals(DeprecationLevel.ERROR, deprecation.level)
    }

    @Test
    fun `plain-value guard message says what to write instead`() {
        val message = plainValueGuard().getAnnotation(Deprecated::class.java).message
        assertTrue("producer" in message!!, "message should name the producer fix, was: $message")
        assertTrue(".with { value }" in message, "message should show the lambda fix, was: $message")
    }

    @Test
    fun `catch-all guard exists and is deprecated at ERROR level`() {
        val deprecation = assertNotNull(catchAllGuard().getAnnotation(Deprecated::class.java))
        assertEquals(DeprecationLevel.ERROR, deprecation.level)
    }

    @Test
    fun `catch-all guard message names the real overloads`() {
        val message = catchAllGuard().getAnnotation(Deprecated::class.java).message
        assertTrue("Kap<A>" in message!!, "message should name the Kap overload, was: $message")
        assertTrue("suspend lambda" in message, "message should name the suspend overload, was: $message")
    }
}
