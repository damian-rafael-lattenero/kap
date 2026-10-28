package kap

import kotlin.time.Duration

// ── 4.1.0: operators a @KapTypeSafe chain may apply mid-way (SPEC F-06) ──

fun <F> KapLike<F>.timeout(duration: Duration, fallback: Kap<F>): Kap<F> = asKap.timeout(duration, fallback)

fun <F> KapLike<F>.timeoutAsFailure(duration: Duration): Kap<F> = asKap.timeoutAsFailure(duration)

infix fun <F> KapLike<F>.orElse(other: Kap<F>): Kap<F> = asKap.orElse(other)

fun <F> KapLike<F>.isolated(): Kap<F> = asKap.isolated()

fun <F> KapLike<F>.traced(name: String, tracer: KapTracer): Kap<F> = asKap.traced(name, tracer)

fun <F> KapLike<F>.named(name: String): Kap<F> = asKap.named(name)

fun <F> KapLike<F>.on(context: kotlin.coroutines.CoroutineContext): Kap<F> = asKap.on(context)
