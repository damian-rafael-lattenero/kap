import kap.*
import kotlinx.coroutines.delay

@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

suspend fun fetchFirstName(): String { delay(30); return "Alice" }
suspend fun fetchLastName(): String { delay(20); return "Smith" }
suspend fun fetchAge(): Int { delay(10); return 30 }

suspend fun main() {
    println("=== KSP Type-Safe Demo ===\n")

    // UNSAFE: same types can be swapped without compile error
    val unsafeResult = Async {
        kap(::User)
            .with { fetchFirstName() }  // String
            .with { fetchLastName() }   // String — swap? no compile error
            .with { fetchAge() }        // Int
    }
    println("  Unsafe: $unsafeResult")

    // SAFE: generated wrapper types prevent swapping
    val safeResult = Async {
        kapSafe(::User)
            .with { UserFirstName(fetchFirstName()) }  // UserFirstName
            .with { UserLastName(fetchLastName()) }     // UserLastName — swap? COMPILE ERROR
            .with { UserAge(fetchAge()) }               // UserAge
    }
    println("  Safe:   $safeResult")

    println("\n  Try swapping UserFirstName and UserLastName — the compiler will reject it!")
}
