# kap-ktor

Ktor server integration plugin for KAP. Shared configuration, circuit breakers, tracing, and response helpers.

!!! warning "Unreleased"
    This module is available in source but **not yet published to Maven Central**. To use it now, build from source or use a JitPack dependency. Maven Central publication is planned for the next release.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-ktor:2.4.0") // coming soon
```

**Depends on:** `kap-core`, `kap-resilience`, `ktor-server-core`.

---

## Plugin Installation

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
        val user = Async {
            Kap { fetchUser(call.parameters["id"]!!) }
                .withCircuitBreaker(breaker)
                .retry(Schedule.times(3) and Schedule.exponential(50.milliseconds))
        }
        call.respond(user)
    }
}
```

## Response Helpers

### respondAsync

Execute a KAP computation and respond:

```kotlin
get("/dashboard/{userId}") {
    call.respondAsync {
        kap(::Dashboard)
            .with { fetchUser(userId) }
            .with { fetchCart(userId) }
            .with { fetchPromos(userId) }
    }
}
```

### respondKap

Execute any suspend block and respond:

```kotlin
get("/user/{id}") {
    call.respondKap {
        Async {
            kap(::UserResponse)
                .with { fetchProfile(id) }
                .with { fetchPreferences(id) }
        }
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
    Requires `ktor-server-status-pages` on your classpath.

## Full Example

```kotlin
fun Application.module() {
    install(ContentNegotiation) { json() }
    install(Kap) {
        tracer = ktorTracer()
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

            val dashboard = Async {
                kap(::Dashboard)
                    .with {
                        Kap { fetchUser(userId) }
                            .withCircuitBreaker(breaker)
                            .traced("fetch-user", tracer)
                    }
                    .with { fetchCart(userId) }
                    .with { fetchPromos(userId) }
            }
            call.respond(dashboard)
        }
    }
}
```

See the [ktor-integration example](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/ktor-integration) for a complete application with 28 integration tests.
