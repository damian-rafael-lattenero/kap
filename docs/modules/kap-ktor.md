# kap-ktor

Ktor server integration plugin for KAP. Shared configuration, circuit breakers, tracing, and response helpers.

!!! info "Published"
    Available on Maven Central since 4.0.0 (see CHANGELOG).

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-ktor:4.1.0")
```

**Depends on:** `kap-core`, `kap-resilience`, `ktor-server-core`, `ktor-server-status-pages` (runtime — `kapExceptionHandlers()` works out of the box).

---

## Plugin Installation

The plugin is published as `Kap`; since 4.1.0 the alias `KapPlugin` is also available for `install(...)` when `kap.Kap` (the computation type) is star-imported in the same file and the names collide:

```kotlin
install(Kap) {
    tracer = KapTracer { event ->
        when (event) {
            is TraceEvent.Started -> logger.info("${event.name} started")
            is TraceEvent.Succeeded -> logger.info("${event.name} in ${event.duration}")
            is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
        }
    }
    circuitBreaker("user-api", maxFailures = 5, resetTimeout = 30.seconds)
    circuitBreaker("payment-api", maxFailures = 3, resetTimeout = 60.seconds)
}
```

## Built-in Tracers

```kotlin
// Standard Ktor logger (SLF4J)
install(Kap) {
    tracer = application.ktorTracer()
}

// Structured key-value output for JSON logging
install(Kap) {
    tracer = application.structuredTracer()
}
```

## Named Circuit Breakers

Register circuit breakers once, use them in any route:

```kotlin
install(Kap) {
    circuitBreaker("user-api", maxFailures = 5, resetTimeout = 30.seconds)
}

routing {
    get("/users/{id}") {
        val breaker = call.circuitBreaker("user-api")
        val user = Kap { fetchUser(call.parameters["id"]!!) }
            .withCircuitBreaker(breaker)
            .retry(Schedule.times(3) and Schedule.exponential(50.milliseconds))
            .evalGraph()
        call.respond(user)
    }
}
```

## Response Helpers

### respondAsync

Execute a KAP computation and respond:

```kotlin
@KapTypeSafe
data class Dashboard(val user: String, val cart: String, val promos: String)

get("/dashboard/{userId}") {
    call.respondAsync {
        kap(::Dashboard)
            .with { user from fetchUser(userId) }
            .with { cart from fetchCart(userId) }
            .with { promos from fetchPromos(userId) }
    }
}
```

### respondKap

Execute any suspend block and respond:

```kotlin
@KapTypeSafe
data class UserResponse(val profile: String, val preferences: String)

get("/user/{id}") {
    call.respondKap {
        kap(::UserResponse)
            .with { profile from fetchProfile(id) }
            .with { preferences from fetchPreferences(id) }
            .evalGraph()
    }
}
```

## StatusPages Exception Handlers

Install KAP-aware exception handlers:

```kotlin
install(StatusPages) {
    kapExceptionHandlers()
    // your custom handlers
}
```

Handles:
| Exception | HTTP Status |
|---|---|
| `CircuitBreakerOpenException` | 503 Service Unavailable |
| `TimeoutCancellationException` | 504 Gateway Timeout |
| `IllegalArgumentException` | 400 Bad Request |

!!! note
    `ktor-server-status-pages` ships as a runtime dependency of `kap-ktor` (since 4.1.0) — `kapExceptionHandlers()` works without adding anything else to your classpath.

## Full Example

```kotlin
fun Application.module() {
    install(ContentNegotiation) { json() }
    install(Kap) {
        tracer = application.ktorTracer()
        circuitBreaker("user-api", maxFailures = 5, resetTimeout = 30.seconds)
    }
    install(StatusPages) {
        kapExceptionHandlers()
    }

    routing {
        get("/dashboard/{userId}") {
            val userId = call.parameters["userId"]!!
            val breaker = call.circuitBreaker("user-api")
            val tracer = call.kapTracer

            val dashboard = kap(::Dashboard)
                .with(DashboardKap.user from
                    Kap { fetchUser(userId) }
                        .withCircuitBreaker(breaker)
                        .traced("fetch-user", tracer)
                )
                .with { cart from fetchCart(userId) }
                .with { promos from fetchPromos(userId) }
                .evalGraph()
            call.respond(dashboard)
        }
    }
}
```

See the [ktor-integration example](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/ktor-integration) for a complete application with 28 integration tests.
