package kap.detekt

import com.intellij.psi.util.PsiTreeUtil
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags a broad catch (`Exception`, `RuntimeException`, `Throwable`) that
 * swallows cancellation: no sibling clause catches
 * [CancellationException][kotlinx.coroutines.CancellationException] first and
 * the body neither arms an `is CancellationException` rethrow nor rethrows
 * outright.
 *
 * The KAP audit's dominant systemic finding: on the JVM
 * [CancellationException][kotlinx.coroutines.CancellationException] extends
 * [RuntimeException], so a catch-all is a cancellation trap — structured
 * concurrency can never cancel through it, and `timeout()` kills are eaten as
 * ordinary failures. The law is mechanical: **arm CE first in every catch-all.**
 *
 * <noncompliant>
 * try {
 *     sealNotifications()
 * } catch (e: Exception) {
 *     logger.warn(e) { "seal failed" }   // cancellation dies here
 * }
 * </noncompliant>
 *
 * <compliant>
 * try {
 *     sealNotifications()
 * } catch (e: CancellationException) {
 *     throw e
 * } catch (e: Exception) {
 *     logger.warn(e) { "seal failed" }
 * }
 * </compliant>
 *
 * <compliant>
 * try {
 *     sealNotifications()
 * } catch (e: Exception) {
 *     if (e is CancellationException) throw e
 *     logger.warn(e) { "seal failed" }
 * }
 * </compliant>
 *
 * The rule is syntactic — it needs no type resolution. SCOPE: only catches
 * inside SUSPEND functions are flagged — the laundering trap needs a
 * suspension point for a CancellationException to flow through; a broad catch
 * in non-suspend code (a file-IO border, a blocking probe) cannot legally
 * receive one from structured concurrency (measured on the fleet audit wave 2:
 * six of seven flagged sites were non-suspend file IO). Known limitations: a
 * broad type reached through an import alias is not recognized, and
 * [runCatching] is not inspected (its lawful replacements are [kap.Kap.settled]
 * and an explicit try/catch with the CE arm).
 */
class CancellationArmBeforeCatchAll(config: Config) : Rule(
    config,
    "Catch-all swallows CancellationException: catch-all arms must rethrow " +
        "CancellationException first — cancellation must always propagate.",
) {

    override fun visitTryExpression(expression: KtTryExpression) {
        super.visitTryExpression(expression)
        if (!expression.isInsideSuspendFunction()) return
        expression.catchClauses.forEachIndexed { index, clause ->
            if (clause.isBroadCatch() && !expression.hasCancellationArmBefore(index, clause)) {
                report(Finding(Entity.from(clause), description))
            }
        }
    }

    /**
     * True when the nearest enclosing function declaration carries the
     * suspend modifier. A non-suspend function cannot call suspend code, so a
     * CancellationException cannot legally reach its catch arms through
     * structured concurrency. (Suspend lambdas passed to non-suspend helpers
     * are out of this syntactic scope: their enclosing named function is
     * non-suspend by the language's own rules.)
     */
    private fun KtTryExpression.isInsideSuspendFunction(): Boolean =
        PsiTreeUtil.getParentOfType(this, KtNamedFunction::class.java)
            ?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true

    private fun KtCatchClause.isBroadCatch(): Boolean =
        catchParameter?.typeReference?.text in BROAD_TYPES

    /**
     * A broad clause is compliant when an earlier sibling catches
     * CancellationException, or the clause body itself arms an
     * `is CancellationException` rethrow, or it rethrows unconditionally.
     */
    private fun KtTryExpression.hasCancellationArmBefore(
        index: Int,
        clause: KtCatchClause,
    ): Boolean = catchClauses.take(index).any { it.catchesCancellation() } ||
        clause.catchBody.hasInlineCancellationArm() ||
        clause.catchBody.hasTopLevelThrow()

    private fun KtCatchClause.catchesCancellation(): Boolean =
        catchParameter?.typeReference?.text?.endsWith(CANCELLATION_SUFFIX) == true

    private fun KtExpression?.hasInlineCancellationArm(): Boolean =
        this != null &&
            (collectDescendantsOfType<KtIsExpression>().any { it.cancellationTypeCheck() } ||
                collectDescendantsOfType<KtWhenConditionIsPattern>().any { it.cancellationTypeCheck() }) &&
            collectDescendantsOfType<KtThrowExpression>().isNotEmpty()

    private fun KtIsExpression.cancellationTypeCheck(): Boolean =
        typeReference?.text?.endsWith(CANCELLATION_SUFFIX) == true

    private fun KtWhenConditionIsPattern.cancellationTypeCheck(): Boolean =
        typeReference?.text?.endsWith(CANCELLATION_SUFFIX) == true

    private fun KtExpression?.hasTopLevelThrow(): Boolean =
        (this as? KtBlockExpression)?.statements?.any { it is KtThrowExpression } == true

    private companion object {
        val BROAD_TYPES = setOf(
            "Exception",
            "RuntimeException",
            "Throwable",
            "java.lang.Exception",
            "java.lang.RuntimeException",
            "java.lang.Throwable",
        )

        const val CANCELLATION_SUFFIX = "CancellationException"
    }
}
