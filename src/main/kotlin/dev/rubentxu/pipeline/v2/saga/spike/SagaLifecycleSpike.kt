package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.domain.saga.CompensationDirective
import dev.rubentxu.pipeline.v2.domain.saga.SAGA_LIFECYCLE_CAPABILITY
import dev.rubentxu.pipeline.v2.domain.saga.SagaCompletionReport
import dev.rubentxu.pipeline.v2.domain.saga.SagaLifecycle
import dev.rubentxu.pipeline.v2.domain.saga.SagaPlan
import dev.rubentxu.pipeline.v2.domain.step.StepCapability

/**
 * The seventh probe: the LIFECYCLE port — a Saga that ended WELL.
 *
 * ## What it adds to the sixth
 *
 * The sixth probe read a run's verdict. This one asks the engine to CLOSE a Saga, and the
 * closing has two sides that used to be one:
 *
 *  - **compensate** — the chain failed, so the engine undoes what it did;
 *  - **complete** — the chain worked, so the engine KEEPS what it did and discharges the
 *    promises to undo it.
 *
 * Until 0.75.0 only the first existed, and a Saga that worked had nowhere to say so: its
 * promises stayed owed for ever, and a resumed run would compensate a Saga that had succeeded.
 *
 * ## ONE handle, TWO endings
 *
 * The port EXTENDS the runtime port instead of replacing it. A consumer that declares
 * [REQUIRED_CAPABILITY] holds one object that can end its Saga either way, and it cannot end
 * up with two handles that disagree about what the Saga owes.
 *
 * ## What it deliberately does NOT do
 *
 * It does not decide whether the Saga may be completed, and it does not mark anything as kept.
 * The plugin knows whether its chain succeeded; the engine knows what that means for the
 * durable state, and a request the durable state cannot corroborate is REFUSED — with the
 * obligation that blocks it and the reason why, not with a silent success.
 */
object SagaLifecycleSpike {

    /**
     * The capability an external Saga Step declares to ask the engine to end its Saga.
     */
    val REQUIRED_CAPABILITY: StepCapability = SAGA_LIFECYCLE_CAPABILITY

    /**
     * Asks the engine to keep the effects of a Saga whose chain worked.
     *
     * The report says which actions were KEPT, and the Saga's state afterwards: settled, or
     * needing a human.
     */
    suspend fun complete(
        lifecycle: SagaLifecycle,
        plan: SagaPlan,
    ): SagaCompletionReport = lifecycle.complete(plan)

    /**
     * The actions whose effects the Saga kept, in arm order.
     */
    fun kept(report: SagaCompletionReport): List<String> = report.kept.map { it.value }

    /** Whether the Saga ended settled, as the report says it. */
    fun isSettled(report: SagaCompletionReport): Boolean =
        report.directive is CompensationDirective.Settled

    /**
     * The reason a human has to decide, when there is one.
     *
     * `null` when the Saga settled: a settled Saga needs nobody.
     */
    fun interventionReason(report: SagaCompletionReport): String? =
        (report.directive as? CompensationDirective.NeedsIntervention)?.reason?.name
}
