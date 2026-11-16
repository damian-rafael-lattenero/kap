# kap-kotest

Test matchers and utilities for testing KAP computations.

!!! info "Published"
    Available on Maven Central since 4.0.0 (see CHANGELOG).

```kotlin
testImplementation("io.github.damian-rafael-lattenero:kap-kotest:4.1.0")
```

**Depends on:** `kap-core`, `kap-resilience`, `kap-stream` (the backpressure law
suite), `kotlinx-coroutines-test`, `kotlin-test`.

---

## Kap Matchers

Assert success or failure of KAP computations:

```kotlin
@Test
fun `fetches user successfully`() = runTest {
    Kap { fetchUser("alice") }.shouldSucceedWith(User("alice"))
}

@Test
fun `fails on unknown user`() = runTest {
    Kap { fetchUser("unknown") }.shouldFailWith<UserNotFoundException>()
}

@Test
fun `returns the value for further assertions`() = runTest {
    val user = Kap { fetchUser("alice") }.shouldSucceed()
    assertEquals("alice", user.name)
}

@Test
fun `fails with specific message`() = runTest {
    Kap<Unit> { error("boom") }.shouldFailWithMessage("boom")
}
```

## Result Matchers

For asserting `Result<T>` values (useful with `.settled()`):

```kotlin
Result.success(42).shouldBeSuccess(42)
Result.success("hello").shouldBeSuccess()  // returns "hello"
Result.failure<Int>(IllegalArgumentException("bad")).shouldBeFailure<IllegalArgumentException>()
Result.failure<Int>(RuntimeException("boom")).shouldBeFailureWithMessage("boom")
```

## Timing Matchers

Prove parallel execution in virtual-time tests:

```kotlin
@Test
fun `runs 5 tasks in parallel`() = runTest {
    items.traverse { Kap { delay(50); it } }.evalGraph()
    currentTime.shouldBeMillis(50, "5 tasks should complete in 50ms, not 250ms")
}

@Test
fun `completes within timeout`() = runTest {
    Kap { delay(80) }.evalGraph()
    currentTime.shouldBeAtMostMillis(100)
}

@Test
fun `proves parallelism`() = runTest {
    Kap.of { a: Unit -> { b: Unit -> { c: Unit -> Triple(a, b, c) } } }
        .with(Kap { delay(50) })
        .with(Kap { delay(50) })
        .with(Kap { delay(50) })
        .evalGraph()
    currentTime.shouldProveParallel(taskCount = 3, taskDurationMs = 50)
}
```

## Resilience Matchers

Assert CircuitBreaker state:

```kotlin
breaker.shouldBeClosed()
breaker.shouldBeOpen()
breaker.shouldBeHalfOpen()
```

Track state transitions:

```kotlin
val tracker = CircuitBreakerTracker()
val breaker = CircuitBreaker(
    maxFailures = 3,
    retryAfter = 1.seconds,
    onStateChange = tracker::record
)

// ... trigger failures ...

tracker.shouldHaveTransitioned(CircuitBreaker.State.Closed to CircuitBreaker.State.Open)
tracker.shouldHaveTransitionCount(1)
```

## Schedule Matchers

Assert a `Schedule.Decision` — the same call the retry drivers make:

```kotlin
val policy = Schedule.times<String>(3)

policy.decideAt(2, "input").shouldContinue()                  // any delay
policy.decideAt(2, "input").shouldContinue(ZERO)              // exact delay
policy.decideAt(3, "input").shouldStop()                      // Decision.Done
```

`shouldContinue(expectedDelay)` compares delays exactly (`Duration` equality);
`shouldStop()` is strict — a `Continue(Duration.INFINITE)` is observationally a
stop (LAWS.md) but still fails the matcher.

## Lifecycle Matchers

Track and assert resource lifecycle events:

```kotlin
val lifecycle = LifecycleTracker()

bracket(
    acquire = { lifecycle.record("acquire"); openDb() },
    use = { lifecycle.record("use"); Kap { it.query() } },
    release = { lifecycle.record("release"); it.close() }
).evalGraph()

lifecycle.shouldHaveEvents("acquire", "use", "release")
lifecycle.shouldHaveReleasedAfterUse("use", "release")
lifecycle.shouldHaveEventCount(3)
```

## Law suites

The same laws that guard KAP's own combinators are reusable against **your**
implementation of a named `Backpressure` or a bounded mailbox. Both suites are
virtual-time only (no sleeps) and are invoked inside `runTest`.

### `backpressureLaws` — any producer with a named `Backpressure`

Implement the one-method harness so it produces `items` through your
implementation's strategy boundary and hands every survivor to `emit`:

```kotlin
class MyProducer : BackpressuredProducer<Int> {
    override suspend fun produce(strategy: Backpressure, items: List<Int>, emit: suspend (Int) -> Unit) {
        // your pipeline, e.g. a Flow/Channel boundary applying `strategy`
        ...
    }
}

@Test
fun `my producer obeys the named backpressure laws`() = runTest {
    backpressureLaws(MyProducer(), items = (1..100).toList()).verify()
}
```

Laws: exact strategies (`Suspend`, `Buffer(n)`) never drop; lossy strategies
drop only what they name (`Conflate`/`Latest(n)` keep the newest,
`DropOldest(n)` the newest `n`, `DropLatest(n)` the already-buffered prefix);
survivors keep upstream order; nothing is duplicated. You can run one law at a
time (`exactStrategiesNeverDrop()`, `lossyStrategiesDropOnlyWhatTheyName()`,
`orderingIsPreserved()`, `noDuplication()`) or pass a subset of `strategies`.

### `consumerLaws` — a bounded mailbox with one consumer

Implement `MailboxUnderTest<A>` over your mailbox. `start` must launch the
single consumer in the **provided** scope (the test's virtual-time scope) and
invoke the handler one item at a time; a failing handler must not stop the
consumer, and an optional per-item `budget` must abandon a slow item without
swallowing cancellation. `dropped` counts every discarded element (superseded
ones included).

```kotlin
class MyMailbox<A>(override val backpressure: Backpressure) : MailboxUnderTest<A> {
    override var dropped: Long = 0
    override suspend fun send(item: A) { ... }

    override fun start(
        scope: CoroutineScope,
        handler: suspend (A) -> Unit,
        budget: Duration?,
    ): MailboxConsumer {
        val job = scope.launch { /* the one consumer loop */ }
        return object : MailboxConsumer {
            override suspend fun stop() = job.cancelAndJoin()
        }
    }
}

@Test
fun `my mailbox obeys the consumer laws`() = runTest {
    consumerLaws({ MyMailbox<Int>(Backpressure.Buffer(4)) }, items = (1..20).toList()).verify()
}
```

Laws: at most one handler in flight; FIFO for exact strategies; a failing item
never stops the next; a per-item budget abandons and continues; stopping the
consumer cancels the in-flight handler (cancellation is never swallowed); and
drop accounting is honest (`delivered + dropped == sent` for lossy strategies,
zero drops for exact ones). The individual laws are public too
(`atMostOneHandlerInFlight()`, `fifoForExactStrategies()`,
`aFailingItemNeverStopsTheNext()`, `perItemBudgetAbandonsAndContinues()`,
`cancellationIsNeverSwallowed()`, `dropAccountingIsHonest()`).

!!! note "Dogfooding"
    `KapStreamLawsTest` runs `backpressureLaws` over the real `KapStream`
    stages, so the suite is exercised by the library that motivated it.

## Arrow Matchers

Available when `kap-arrow` is on the test classpath:

```kotlin
val right: Either<String, Int> = Either.Right(42)
right.shouldBeRight(42)
right.shouldBeRight()  // returns 42

val left: Either<String, Int> = Either.Left("error")
left.shouldBeLeft("error")

// Validation error accumulation
val result: Either<NonEmptyList<RegError>, User> = zipV(...) { ... }.evalGraph()
result.shouldHaveErrors(3)
result.shouldContainError(RegError.NameTooShort(2))
```
