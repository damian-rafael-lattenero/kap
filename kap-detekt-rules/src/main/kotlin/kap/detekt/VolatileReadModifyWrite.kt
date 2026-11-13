package kap.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags a [Volatile][kotlin.concurrent.Volatile] property used as a
 * read-modify-write accumulator: `x++`, `x += n`, `x = x + 1`, `x = f(x)`.
 *
 * The kap audit law: `@Volatile` guarantees visibility of single reads and
 * writes — nothing more. A read-then-write sequence on a shared `@Volatile`
 * cell races and loses writes; the lawful homes for shared RMW state are kap
 * [kap.Ref] (suspending, mutex-backed, for graph code) and
 * `Ref.sync` / [kap.SyncCell] (lock-free compare-and-set, for SPIs that
 * cannot suspend). A `@Volatile` cell owned by a single writer that everyone
 * else only reads is fine — this rule never flags plain stores or reads.
 *
 * <noncompliant>
 * @Volatile var events = 0          // two workers: writes are lost
 * fun onEvent() { events++ }
 * </noncompliant>
 *
 * <compliant>
 * private val events = Ref.sync(0)  // lock-free; same semantics as Ref
 * fun onEvent() { events.getAndUpdate { it + 1 } }
 * </compliant>
 *
 * <compliant>
 * @Volatile var running = false     // single-writer flag: legal
 * fun stop() { running = false }
 * </compliant>
 *
 * The rule is syntactic and per-file — it matches the property by name, so a
 * same-named local shadowing the field in an unrelated scope is reported
 * (rare; rename or extract). Usages in other files of the same property are
 * not visible to a syntactic rule.
 */
class VolatileReadModifyWrite(config: Config) : Rule(
    config,
    "@Volatile property used as a read-modify-write accumulator: concurrent " +
        "updates race and lose writes. Use kap Ref (suspend) or Ref.sync/SyncCell " +
        "(atomic) for shared RMW state.",
) {

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val volatileNames = file.collectDescendantsOfType<KtProperty>()
            .filter { it.isVolatileAnnotated() }
            .mapNotNull { it.name }
            .toSet()
        if (volatileNames.isEmpty()) return

        file.collectDescendantsOfType<KtUnaryExpression>()
            .filter { it.isRmwOn(volatileNames) }
            .forEach { report(Finding(Entity.from(it), description)) }

        file.collectDescendantsOfType<KtBinaryExpression>()
            .filter { it.isRmwOn(volatileNames) }
            .forEach { report(Finding(Entity.from(it), description)) }
    }

    private fun KtProperty.isVolatileAnnotated(): Boolean =
        annotationEntries.any { it.shortName?.asString() == VOLATILE }

    private fun KtUnaryExpression.isRmwOn(names: Set<String>): Boolean =
        operationReference.text in INCREMENT_OPS && baseExpression?.text in names

    private fun KtBinaryExpression.isRmwOn(names: Set<String>): Boolean {
        val left = left?.text
        return when (operationReference.text) {
            in COMPOUND_ASSIGNMENTS -> left in names
            ASSIGNMENT -> left in names && right.readsAny(names)
            else -> false
        }
    }

    private fun org.jetbrains.kotlin.psi.KtExpression?.readsAny(names: Set<String>): Boolean =
        this != null &&
            collectDescendantsOfType<KtNameReferenceExpression>()
                .any { it.getReferencedName() in names }

    private companion object {
        const val VOLATILE = "Volatile"
        const val ASSIGNMENT = "="

        val INCREMENT_OPS = setOf("++", "--")

        val COMPOUND_ASSIGNMENTS = setOf("+=", "-=", "*=", "/=", "%=")
    }
}
