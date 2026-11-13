package kap.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Registers the KAP doctrine rules under the `kap` rule set.
 *
 * Loaded through the [ServiceLoader][java.util.ServiceLoader] entry in
 * `META-INF/services/dev.detekt.api.RuleSetProvider`.
 */
class KapRuleSetProvider : RuleSetProvider {

    override val ruleSetId: RuleSetId = RuleSetId("kap")

    override fun instance(): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(::ManualWaitLoop, ::CancellationArmBeforeCatchAll, ::VolatileReadModifyWrite),
        )
}
