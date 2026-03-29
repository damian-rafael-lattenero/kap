# kap-kotest

Test matchers and utilities for testing KAP computations.

!!! warning "Unreleased"
    This module is available in source but **not yet published to Maven Central**. To use it now, build from source or use a JitPack dependency. Maven Central publication is planned for the next release.

```kotlin
testImplementation("io.github.damian-rafael-lattenero:kap-kotest:2.5.0") // coming soon
```

**Depends on:** `kap-core`, `kap-resilience`, `kotlinx-coroutines-test`, `kotlin-test`.

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
    Async { items.traverse { Kap { delay(50); it } } }
    currentTime.shouldBeMillis(50, "5 tasks should complete in 50ms, not 250ms")
}

@Test
fun `completes within timeout`() = runTest {
    Async { Kap { delay(80) } }
    currentTime.shouldBeAtMostMillis(100)
}

@Test
fun `proves parallelism`() = runTest {
    Async {
        kap { a: Unit, b: Unit, c: Unit -> Triple(a, b, c) }
            .with(Kap { delay(50) })
            .with(Kap { delay(50) })
            .with(Kap { delay(50) })
    }
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
    resetTimeout = 1.seconds,
    onStateChange = tracker::record
)

// ... trigger failures ...

tracker.shouldHaveTransitioned(CircuitBreaker.State.Closed to CircuitBreaker.State.Open)
tracker.shouldHaveTransitionCount(1)
```

## Lifecycle Matchers

Track and assert resource lifecycle events:

```kotlin
val lifecycle = LifecycleTracker()

Async {
    bracket(
        acquire = { lifecycle.record("acquire"); openDb() },
        use = { lifecycle.record("use"); Kap { it.query() } },
        release = { lifecycle.record("release"); it.close() }
    )
}

lifecycle.shouldHaveEvents("acquire", "use", "release")
lifecycle.shouldHaveReleasedAfterUse("use", "release")
lifecycle.shouldHaveEventCount(3)
```

## Arrow Matchers

Available when `kap-arrow` is on the test classpath:

```kotlin
val right: Either<String, Int> = Either.Right(42)
right.shouldBeRight(42)
right.shouldBeRight()  // returns 42

val left: Either<String, Int> = Either.Left("error")
left.shouldBeLeft("error")

// Validation error accumulation
val result: Either<NonEmptyList<RegError>, User> = Async { zipV(...) { ... } }
result.shouldHaveErrors(3)
result.shouldContainError(RegError.NameTooShort(2))
```
