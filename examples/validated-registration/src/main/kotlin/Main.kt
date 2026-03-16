import applicative.*
import kotlinx.coroutines.delay

/**
 * User registration with parallel validation and error accumulation.
 *
 * Demonstrates: liftV+apV for validation with error accumulation,
 * typed validation results via wrapper data classes,
 * and how ALL errors are collected (not just the first).
 */

// ── Typed validation results ─────────────────────────────────────────────

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class ValidPassword(val value: String)

// ── Error type ───────────────────────────────────────────────────────────

sealed class RegError(val message: String) {
    class InvalidName(message: String) : RegError(message)
    class InvalidEmail(message: String) : RegError(message)
    class InvalidAge(message: String) : RegError(message)
    class WeakPassword(message: String) : RegError(message)
    class UsernameTaken(message: String) : RegError(message)
}

// ── Domain type ──────────────────────────────────────────────────────────

data class User(val name: ValidName, val email: ValidEmail, val age: ValidAge, val username: ValidUsername)

// ── Validation functions (simulate async checks) ────────────────────────

suspend fun validateName(name: String): Either<Nel<RegError>, ValidName> {
    delay(50)
    return if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(RegError.InvalidName("Name must be at least 2 characters").nel())
}

suspend fun validateEmail(email: String): Either<Nel<RegError>, ValidEmail> {
    delay(60)
    return if ("@" in email && "." in email) Either.Right(ValidEmail(email))
    else Either.Left(RegError.InvalidEmail("Invalid email format").nel())
}

suspend fun validateAge(age: Int): Either<Nel<RegError>, ValidAge> {
    delay(30)
    return if (age in 13..120) Either.Right(ValidAge(age))
    else Either.Left(RegError.InvalidAge("Age must be between 13 and 120").nel())
}

suspend fun validatePassword(password: String): Either<Nel<RegError>, ValidPassword> {
    delay(40)
    val errors = mutableListOf<RegError>()
    if (password.length < 8) errors.add(RegError.WeakPassword("Password must be at least 8 characters"))
    if (!password.any { it.isDigit() }) errors.add(RegError.WeakPassword("Password must contain a digit"))
    if (!password.any { it.isUpperCase() }) errors.add(RegError.WeakPassword("Password must contain an uppercase letter"))
    return if (errors.isEmpty()) Either.Right(ValidPassword(password))
    else Either.Left(Nel(errors.first(), errors.drop(1)))
}

suspend fun checkUsernameAvailable(username: String): Either<Nel<RegError>, ValidUsername> {
    delay(100) // simulate DB lookup
    val taken = setOf("admin", "root", "alice")
    return if (username.lowercase() !in taken) Either.Right(ValidUsername(username))
    else Either.Left(RegError.UsernameTaken("Username '$username' is already taken").nel())
}

suspend fun main() {
    // ── Scenario 1: All validations pass ──
    println("=== Scenario 1: Valid registration ===\n")

    // Type safety: swap any two .apV calls and it won't compile!
    // The types ValidName, ValidEmail, ValidAge, ValidUsername are all distinct,
    // so the compiler enforces the correct order of arguments.
    val result1 = Async {
        liftV4<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .apV { validateName("Alice") }
            .apV { validateEmail("alice@example.com") }
            .apV { validateAge(28) }
            .apV { checkUsernameAvailable("alice_new") }
    }

    when (result1) {
        is Either.Right -> println("  Success: ${result1.value}")
        is Either.Left -> println("  Errors: ${result1.value.joinToString { it.message }}")
    }

    // ── Scenario 2: Multiple validations fail ──
    println("\n=== Scenario 2: Multiple failures (all errors collected) ===\n")

    val result2 = Async {
        liftV4<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .apV { validateName("A") }           // too short
            .apV { validateEmail("not-an-email") } // no @
            .apV { validateAge(5) }               // too young
            .apV { checkUsernameAvailable("admin") } // taken
    }

    when (result2) {
        is Either.Right -> println("  Success: ${result2.value}")
        is Either.Left -> {
            println("  ${result2.value.size} errors found:")
            result2.value.forEach { println("    - ${it.message}") }
        }
    }

    // ── Scenario 3: Phased validation (validate fields, then check availability) ──
    println("\n=== Scenario 3: Phased validation with flatMapV ===\n")

    val result3 = Async {
        liftV3<RegError, ValidName, ValidEmail, ValidAge, Triple<ValidName, ValidEmail, ValidAge>>(::Triple)
            .apV { validateName("Bob") }
            .apV { validateEmail("bob@test.com") }
            .apV { validateAge(25) }
            .flatMapV { (name, email, age) ->
                Computation { checkUsernameAvailable("alice") }.mapV { username ->
                    User(name, email, age, username)
                }
            }
    }

    when (result3) {
        is Either.Right -> println("  Success: ${result3.value}")
        is Either.Left -> {
            println("  ${result3.value.size} errors found:")
            result3.value.forEach { println("    - ${it.message}") }
        }
    }

    // ── Scenario 4: Password with multiple errors ──
    println("\n=== Scenario 4: Password with multiple rule violations ===\n")

    val result4 = Async {
        liftV3<RegError, ValidName, ValidEmail, ValidPassword, String> { name, email, _ ->
            "${name.value} (${email.value}) - password accepted"
        }
            .apV { validateName("Charlie") }
            .apV { validateEmail("charlie@test.com") }
            .apV { validatePassword("abc") }  // too short, no digit, no uppercase
    }

    when (result4) {
        is Either.Right -> println("  Success: ${result4.value}")
        is Either.Left -> {
            println("  ${result4.value.size} errors found:")
            result4.value.forEach { println("    - ${it.message}") }
        }
    }
}
