package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.domain.saga.CompensationDirective
import dev.rubentxu.pipeline.v2.domain.saga.InterventionReason
import dev.rubentxu.pipeline.v2.domain.saga.SagaCompensationReport

/**
 * The fourth probe: what a consumer does when the ENGINE says it did not dispatch.
 *
 * ## Why this probe exists
 *
 * The first three probes asked what a Saga PLEDGES, what it OWES and how it is COMPENSATED.
 * This one asks the question that decides whether an operator can press "retry": did the
 * compensation run or not?
 *
 * The two answers are not interchangeable:
 *
 *  - [InterventionReason.COMPENSATION_NOT_DISPATCHED] means the durable state refused the
 *    write-ahead, so the body never left the process. The resource was NOT touched, and the
 *    obligation is still owed: retrying is safe.
 *  - [InterventionReason.COMPENSATION_UNCERTAIN] means something ran and nobody knows what it
 *    did. Retrying could apply the compensation a second time, so it is not safe.
 *
 * A consumer that cannot tell those two apart either retries something that already ran — and
 * undoes a resource twice — or refuses to retry something that never ran, and leaves the Saga
 * stuck forever. So the distinction has to be reachable from the report, and this probe is
 * where an external consumer proves it is.
 *
 * ## What it deliberately does NOT do
 *
 * It does not decide anything about the Saga's durable state, and it does not compensate
 * anything. It reads the report the engine produced and turns it into the one question a
 * caller actually has: "may I try again?".
 */
object SagaRefusalSpike {

    /**
     * Why the Saga stopped, when it stopped because a human is needed.
     *
     * `null` when the Saga settled, or when it stopped for a reason that is not an
     * intervention: the report says what happened, and this does not widen it.
     */
    fun interventionReason(report: SagaCompensationReport): InterventionReason? =
        (report.directive as? CompensationDirective.NeedsIntervention)?.reason

    /**
     * Whether the Saga may be retried without risking a double compensation.
     *
     * True only when the engine says the compensation was NOT dispatched: nothing ran, so
     * running it again is not a second undo, it is the first one.
     */
    fun isSafeToRetry(report: SagaCompensationReport): Boolean =
        interventionReason(report) == InterventionReason.COMPENSATION_NOT_DISPATCHED

    /**
     * Whether the Saga stopped on a compensation whose outcome nobody knows.
     *
     * This is the case that must NOT be retried blindly: the compensation may already have
     * been applied, and applying it again is how a Saga undoes something twice.
     */
    fun isUncertain(report: SagaCompensationReport): Boolean =
        interventionReason(report) == InterventionReason.COMPENSATION_UNCERTAIN
}
