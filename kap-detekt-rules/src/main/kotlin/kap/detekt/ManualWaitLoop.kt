package kap.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags hand-rolled wait loops: a `while (true)` whose body waits with
 * `kotlinx.coroutines.delay(...)` or `Thread.sleep(...)`.
 *
 * The KAP doctrine forbids polling loops as control flow: a cadence must be a
 * value ([Schedule][kap.Schedule]), interpreted by `repeat`/`retry`, so the
 * delay, the stop condition and cancellation are policy instead of code.
 *
 * <noncompliant>
 * while (true) {
 *     val job = api.poll()
 *     if (job != null) break
 *     delay(1.seconds)
 * }
 * </noncompliant>
 *
 * <compliant>
 * Kap { api.poll() }
 *     .repeatWhile { it == null }
 *     .retry(Schedule.spaced(1.seconds))
 * </compliant>
 *
 * `delay` is matched only when it is the coroutines delay (imported from
 * `kotlinx.coroutines`, star-imported, or fully qualified); a same-named local
 * function is not reported. `Thread.sleep` must be called on `Thread` or
 * `java.lang.Thread`. The rule is syntactic — it needs no type resolution.
 */
class ManualWaitLoop(config: Config) : Rule(
    config,
    "Manual wait loop: a `while (true)` body waits with delay()/Thread.sleep(). " +
        "Model the cadence as a Schedule and use repeat/retry instead.",
) {

    override fun visitWhileExpression(expression: KtWhileExpression) {
        super.visitWhileExpression(expression)
        if (expression.isUnboundedLoop() && expression.bodyHasWait()) {
            report(Finding(Entity.from(expression), description))
        }
    }

    private fun KtWhileExpression.isUnboundedLoop(): Boolean =
        condition?.text == "true"

    private fun KtWhileExpression.bodyHasWait(): Boolean =
        body?.collectDescendantsOfType<KtCallExpression>()?.any { it.isWaitCall() } == true

    private fun KtCallExpression.isWaitCall(): Boolean =
        when (calleeExpression?.text) {
            "delay" -> isCoroutineDelay()
            "sleep" -> isThreadSleep()
            else -> false
        }

    private fun KtCallExpression.isCoroutineDelay(): Boolean {
        val qualified = parent as? KtDotQualifiedExpression
        if (qualified != null) {
            return qualified.selectorExpression == this &&
                qualified.receiverExpression.text == "kotlinx.coroutines"
        }
        val file = containingFile as? KtFile ?: return false
        return file.importDirectives.any { directive ->
            val fqName = directive.importedFqName?.asString()
            fqName == "kotlinx.coroutines.delay" ||
                (fqName == "kotlinx.coroutines" && directive.isAllUnder)
        }
    }

    private fun KtCallExpression.isThreadSleep(): Boolean {
        val qualified = parent as? KtDotQualifiedExpression ?: return false
        if (qualified.selectorExpression != this) return false
        return qualified.receiverExpression.text == "Thread" ||
            qualified.receiverExpression.text == "java.lang.Thread"
    }
}
