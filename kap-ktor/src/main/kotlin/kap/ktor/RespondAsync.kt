package kap.ktor

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kap.Async
import kap.Kap

/**
 * Execute a KAP computation and respond with the result.
 *
 * ```kotlin
 * get("/dashboard/{userId}") {
 *     call.respondAsync {
 *         kap(::Dashboard)
 *             .with { fetchUser(userId) }
 *             .with { fetchCart(userId) }
 *     }
 * }
 * ```
 */
suspend inline fun <reified T : Any> RoutingCall.respondAsync(
    status: HttpStatusCode = HttpStatusCode.OK,
    block: () -> Kap<T>
) {
    val kap = block()
    val result = Async { kap }
    respond(status, result)
}

/**
 * Execute a suspend block and respond with the result.
 *
 * ```kotlin
 * get("/user/{id}") {
 *     call.respondKap {
 *         Async {
 *             kap(::UserResponse)
 *                 .with { fetchProfile(id) }
 *                 .with { fetchPreferences(id) }
 *         }
 *     }
 * }
 * ```
 */
suspend inline fun <reified T : Any> RoutingCall.respondKap(
    status: HttpStatusCode = HttpStatusCode.OK,
    crossinline block: suspend () -> T
) {
    val result = block()
    respond(status, result)
}
