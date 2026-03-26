package kap

/**
 * JVM actual: overrides [fillInStackTrace] to avoid the cost of capturing
 * a full stack trace for control-flow-only exceptions.
 */
actual open class ControlFlowException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}
