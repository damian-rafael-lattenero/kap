package applicative

/**
 * Base class for internal control-flow exceptions that never escape the library.
 *
 * On JVM, [fillInStackTrace] is overridden to be a no-op — these exceptions are
 * used purely for control flow (e.g., [ValidatedShortCircuit]) and filling the
 * stack trace would be a significant performance penalty in hot validation paths.
 *
 * On JS and Native, this is a plain [Exception] (those platforms either don't have
 * [fillInStackTrace] or it's already a no-op).
 */
internal expect open class ControlFlowException() : Exception
