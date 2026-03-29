# Coming from Raw Coroutines

If you're using `coroutineScope { async { } }` for parallel execution, this guide shows how KAP simplifies your code while keeping all the structured concurrency guarantees you rely on.

## KAP is built ON coroutines

KAP doesn't replace `kotlinx.coroutines` — it uses it internally. `Async { }` creates a `coroutineScope`. `.with` uses `async`. All structured concurrency rules still apply:

- Parent cancels → all children cancel
- One child fails → siblings cancel (unless `.settled()`)
- `CancellationException` is never caught
- `CoroutineContext` propagates to all branches

## Simple parallel: `async`/`await` → `kap` + `.with`

=== "Raw Coroutines"

    ```kotlin
    val result = coroutineScope {
        val dUser = async { fetchUser() }
        val dCart = async { fetchCart() }
        val dPromos = async { fetchPromos() }
        Dashboard(dUser.await(), dCart.await(), dPromos.await())
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        kap(::Dashboard)
            .with { fetchUser() }
            .with { fetchCart() }
            .with { fetchPromos() }
    }
    ```

6 lines → 4 lines. No shuttle variables. Swap two `.with` lines? Compile error.

## Phased execution: nested `coroutineScope` → `.then`

=== "Raw Coroutines"

    ```kotlin
    // Where does phase 1 end? Read every line to find out.
    val result = coroutineScope {
        val dA = async { fetchA() }
        val dB = async { fetchB() }
        val a = dA.await()
        val b = dB.await()
        val validated = validate(a, b)   // invisible barrier
        val dC = async { fetchC() }
        val dD = async { fetchD() }
        Result(a, b, validated, dC.await(), dD.await())
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        kap(::Result)
            .with { fetchA() }       // ┐ phase 1
            .with { fetchB() }       // ┘
            .then { validate() }     // ── explicit barrier
            .with { fetchC() }       // ┐ phase 2
            .with { fetchD() }       // ┘
    }
    ```

## Bounded concurrency: `Semaphore` → `traverse(concurrency)`

=== "Raw Coroutines"

    ```kotlin
    val semaphore = Semaphore(10)
    val results = coroutineScope {
        userIds.map { id ->
            async {
                semaphore.withPermit { fetchUser(id) }
            }
        }.awaitAll()
    }
    ```

=== "KAP"

    ```kotlin
    val results = Async {
        userIds.traverse(concurrency = 10) { id ->
            Kap { fetchUser(id) }
        }
    }
    ```

## Timeout with fallback: `withTimeoutOrNull` → `.timeout`

=== "Raw Coroutines"

    ```kotlin
    val result = withTimeoutOrNull(500) { fetchSlowService() } ?: "fallback"
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { fetchSlowService() }.timeout(500.milliseconds) { "fallback" }
    }
    ```

## Parallel fallback: sequential → `timeoutRace`

=== "Raw Coroutines"

    ```kotlin
    // Sequential: waste 100ms before starting fallback
    val result = try {
        withTimeout(100) { fetchFromPrimary() }
    } catch (e: TimeoutCancellationException) {
        fetchFromFallback()  // starts AFTER timeout
    }
    ```

=== "KAP"

    ```kotlin
    // Parallel: both start at t=0
    val result = Async {
        Kap { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
    }
    // 2.6x faster — fallback already running when primary times out
    ```

## Error recovery: `try`/`catch` → `.recover`

=== "Raw Coroutines"

    ```kotlin
    val result = try {
        fetchUser()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        "anonymous"
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { fetchUser() }.recover { "anonymous" }
    }
    // CancellationException automatically re-thrown — no manual check needed
    ```

## Retry: manual loop → `Schedule`

=== "Raw Coroutines"

    ```kotlin
    var result: String? = null
    var lastException: Exception? = null
    repeat(3) { attempt ->
        try {
            result = fetchUser()
            return@repeat
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastException = e
            delay(100L * (attempt + 1))  // linear backoff, hardcoded
        }
    }
    result ?: throw lastException!!
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { fetchUser() }.retry(
            Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds).jittered()
        )
    }
    ```

## Resource cleanup: `try`/`finally` → `bracket`

=== "Raw Coroutines"

    ```kotlin
    val conn = openConnection()
    try {
        conn.query("SELECT 1")
    } finally {
        conn.close()  // not NonCancellable — cancellation can skip this!
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        bracket(
            acquire = { openConnection() },
            use = { conn -> Kap { conn.query("SELECT 1") } },
            release = { conn -> conn.close() },  // NonCancellable — guaranteed
        )
    }
    ```

## Partial failure: `supervisorScope` → `.settled()`

=== "Raw Coroutines"

    ```kotlin
    val result = supervisorScope {
        val dUser = async { fetchUserMayFail() }
        val dCart = async { fetchCart() }
        val user = try { dUser.await() } catch (e: Exception) { "anonymous" }
        val cart = dCart.await()
        Dashboard(user, cart)
    }
    ```

=== "KAP"

    ```kotlin
    // Builder function: handles the Result wrapper explicitly
    fun buildDashboard(user: Result<String>, cart: String): Dashboard =
        Dashboard(user.getOrDefault("anonymous"), cart)

    val result = Async {
        kap(::buildDashboard)
            .with(settled { fetchUserMayFail() })  // .settled() → Result<String>
            .with { fetchCart() }                          // normal String
    }
    // fetchUserMayFail() fails → Result.failure → buildDashboard uses "anonymous"
    // fetchCart() is NOT cancelled
    ```

## Cheat sheet

| Raw Coroutines | KAP |
|---|---|
| `coroutineScope { async { } }` | `Async { kap(::T).with { } }` |
| `async { }.await()` | `.with { }` |
| suspend call between phases | `.then { }` |
| nested `coroutineScope` | `.andThen { ctx -> }` |
| `Semaphore` + `async` | `traverse(concurrency)` |
| `withTimeoutOrNull` | `.timeout(d) { default }` |
| `try/catch` | `.recover { }` |
| `try/finally` | `bracket(acquire, use, release)` |
| `supervisorScope` | `.settled()` |
| `select { }` | `race()` / `raceN()` |
| manual retry loop | `retry(Schedule)` |
