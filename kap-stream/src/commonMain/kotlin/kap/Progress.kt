package kap

/**
 * The report of one drain poll: how much work **this poll** handed over, and
 * whether the source came back quiet.
 *
 * A drain is a loop that asks a source for work until the source has none left.
 * The loop needs two answers per poll, and [Progress] carries both: [total] is
 * what to account for (a `pollUntilQuiet` emits exactly the reports that handed
 * work over) and [quiet] is what to decide on (a `Schedule.debounce` window
 * counts the consecutive quiet reports and ends the drain when the window
 * fills).
 *
 * ```
 * val report = Progress(total = drained, quiet = drained == 0)
 * ```
 *
 * @property total the number of items **this poll** handed over, non-negative.
 *   The drain's grand total is the sum of the emitted reports' `total`, not a
 *   running figure carried in the report.
 * @property quiet whether the source reported nothing left to drain. A quiet
 *   report is never emitted by `KapStream.pollUntilQuiet`: it carries nothing
 *   to emit.
 */
data class Progress(val total: Int, val quiet: Boolean = false) {

    init {
        require(total >= 0) { "Progress.total must be >= 0, was $total" }
    }

    companion object {
        /** The empty report: nothing handed over, the source is quiet. */
        val Quiet: Progress = Progress(0, quiet = true)
    }
}
