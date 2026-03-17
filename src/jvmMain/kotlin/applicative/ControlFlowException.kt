package applicative

/**
 * JVM actual: overrides [fillInStackTrace] to avoid the cost of capturing
 * a full stack trace for control-flow-only exceptions.
 */
internal actual open class ControlFlowException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}
