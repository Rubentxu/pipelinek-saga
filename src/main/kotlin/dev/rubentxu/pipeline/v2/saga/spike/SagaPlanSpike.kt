package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.domain.saga.CompensationDirective
import dev.rubentxu.pipeline.v2.domain.saga.CompensationKey
import dev.rubentxu.pipeline.v2.domain.saga.CompensationObservation
import dev.rubentxu.pipeline.v2.domain.saga.CompensationPolicy
import dev.rubentxu.pipeline.v2.domain.saga.EffectEvidence
import dev.rubentxu.pipeline.v2.domain.saga.SAGA_PLAN_CAPABILITY
import dev.rubentxu.pipeline.v2.domain.saga.SagaId
import dev.rubentxu.pipeline.v2.domain.saga.SagaPlanOperations
import dev.rubentxu.pipeline.v2.domain.saga.SagaRecordOutcome
import dev.rubentxu.pipeline.v2.domain.step.StepCapability

/**
 * The second probe: the PLAN port, as an external consumer sees it.
 *
 * ## What it adds to the first probe
 *
 * The first probe asked the engine what a single obligation means. This one asks what the
 * Saga as a WHOLE owes now — which is the question a plugin cannot answer on its own, because
 * the answer depends on the durable state of every obligation of the Saga, and that state
 * lives in the engine's database.
 *
 * ## What it proves
 *
 * That an external consumer can drive the lifecycle of a promise through a published port:
 * declare that a call is about to leave the process, report what it turned out to be, and
 * read back what the Saga owes — without importing a single internal class.
 *
 * ## The write-ahead rule is the consumer's to honour
 *
 * [beginForward] exists because of the crash window: between "the call left" and "the answer
 * was written down" a process can die, and then nobody knows whether the effect happened. A
 * consumer that reports the outcome WITHOUT declaring the dispatch first leaves that window
 * uncovered, and a Saga that pays for it may skip a compensation it owes.
 *
 * So the order is not a convention: [beginForward] first, [reportForward] second.
 */
object SagaPlanSpike {

    /**
     * The capability an external Saga Step declares to reach this port.
     *
     * It is a DIFFERENT capability from the intent one, and the difference is the point:
     * making a promise and moving a promise along its lifecycle are different authorities.
     */
    val REQUIRED_CAPABILITY: StepCapability = SAGA_PLAN_CAPABILITY

    /**
     * What the Saga owes NOW.
     *
     * The answer comes from the engine because the state it is derived from is the engine's.
     * The policy is the plugin's: whether an outcome that is not established can be settled
     * by querying the provider depends on the provider, and only the plugin knows it.
     */
    fun owed(
        plan: SagaPlanOperations,
        sagaId: SagaId,
        policy: CompensationPolicy,
    ): CompensationDirective = plan.next(sagaId, policy)

    /**
     * Declares that the forward effect is about to be dispatched.
     *
     * Call it BEFORE the call leaves the process: this is the write that survives the crash
     * that happens while the answer is still in flight.
     */
    fun beginForward(
        plan: SagaPlanOperations,
        key: CompensationKey,
    ): SagaRecordOutcome = plan.beginForward(key)

    /**
     * Reports what the forward effect turned out to be.
     */
    fun reportForward(
        plan: SagaPlanOperations,
        key: CompensationKey,
        evidence: EffectEvidence,
    ): SagaRecordOutcome = plan.recordForward(key, evidence)

    /**
     * Declares that the compensation is about to be dispatched.
     */
    fun beginCompensation(
        plan: SagaPlanOperations,
        key: CompensationKey,
    ): SagaRecordOutcome = plan.beginCompensation(key)

    /**
     * Reports what the compensation turned out to be.
     */
    fun reportCompensation(
        plan: SagaPlanOperations,
        key: CompensationKey,
        observation: CompensationObservation,
    ): SagaRecordOutcome = plan.recordCompensation(key, observation)
}
