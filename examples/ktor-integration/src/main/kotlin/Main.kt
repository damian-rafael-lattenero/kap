/**
 * Ktor integration example — shows how coroutines-applicatives fits inside
 * a real HTTP framework. Three endpoints demonstrate the main patterns:
 *
 * 1. GET /dashboard/{userId}  — multi-phase parallel aggregation (lift+ap+followedBy)
 * 2. POST /register           — parallel validation with error accumulation (zipV)
 * 3. GET /fast-user/{userId}  — resilience stack (timeout+retry+recover)
 */
import applicative.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

// ── Domain types ────────────────────────────────────────────────────────

@Serializable
data class Dashboard(
    val user: UserProfile,
    val cart: Cart,
    val recommendations: List<String>,
    val notifications: Int,
)

@Serializable
data class UserProfile(val id: String, val name: String, val tier: String)

@Serializable
data class Cart(val items: Int, val total: Double)

@Serializable
data class RegistrationRequest(val name: String, val email: String, val age: Int)

@Serializable
data class RegistrationResult(val name: String, val email: String, val age: Int)

@Serializable
data class ErrorResponse(val errors: List<String>)

// ── Simulated services (replace with real HTTP clients) ─────────────────

suspend fun fetchUserProfile(userId: String): UserProfile {
    delay(50) // simulate network
    return UserProfile(userId, "Alice", "Gold")
}

suspend fun fetchCart(userId: String): Cart {
    delay(40)
    return Cart(items = 3, total = 149.99)
}

suspend fun fetchRecommendations(tier: String): List<String> {
    delay(60)
    return listOf("Product A", "Product B", "Product C")
}

suspend fun countNotifications(userId: String): Int {
    delay(30)
    return 7
}

fun validateName(name: String): Either<NonEmptyList<String>, String> =
    if (name.length >= 2) Either.Right(name)
    else Either.Left(NonEmptyList("Name must be at least 2 characters"))

fun validateEmail(email: String): Either<NonEmptyList<String>, String> =
    if ("@" in email) Either.Right(email)
    else Either.Left(NonEmptyList("Invalid email format"))

fun validateAge(age: Int): Either<NonEmptyList<String>, Int> =
    if (age >= 18) Either.Right(age)
    else Either.Left(NonEmptyList("Must be at least 18 years old"))

// ── Ktor application ───────────────────────────────────────────────────

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) { json() }
        install(StatusPages) {
            exception<ValidationException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(cause.errors.toList().map { it.toString() })
                )
            }
        }

        routing {
            // ── 1. Multi-phase parallel aggregation ─────────────────────
            //
            // Phase 1: fetch user + cart in parallel
            // Phase 2 (barrier): fetch recommendations needs user.tier
            //          + notifications independent but gated to phase 2
            get("/dashboard/{userId}") {
                val userId = call.parameters["userId"]!!

                data class Phase1(val user: UserProfile, val cart: Cart)

                val dashboard = Async {
                    lift2(::Phase1)
                        .ap { fetchUserProfile(userId) }    // ┐ phase 1: parallel
                        .ap { fetchCart(userId) }            // ┘
                        .flatMap { p1 ->                     // barrier: need user.tier
                            lift2 { recs: List<String>, notifs: Int ->
                                Dashboard(p1.user, p1.cart, recs, notifs)
                            }
                                .ap { fetchRecommendations(p1.user.tier) }  // ┐ phase 2
                                .ap { countNotifications(userId) }           // ┘
                        }
                }

                call.respond(dashboard)
            }

            // ── 2. Parallel validation with error accumulation ──────────
            post("/register") {
                val req = call.receive<RegistrationRequest>()

                val result = Async {
                    zipV(
                        { validateName(req.name) },
                        { validateEmail(req.email) },
                        { validateAge(req.age) },
                    ) { name: String, email: String, age: Int -> RegistrationResult(name, email, age) }
                        .orThrow()
                }

                call.respond(HttpStatusCode.Created, result)
            }

            // ── 3. Resilience stack ─────────────────────────────────────
            get("/fast-user/{userId}") {
                val userId = call.parameters["userId"]!!

                val user = Async {
                    Computation { fetchUserProfile(userId) }
                        .timeout(200.milliseconds)
                        .retry(3, delay = 50.milliseconds)
                        .recover { UserProfile(userId, "Cached User", "Basic") }
                }

                call.respond(user)
            }
        }
    }.start(wait = true)
}
