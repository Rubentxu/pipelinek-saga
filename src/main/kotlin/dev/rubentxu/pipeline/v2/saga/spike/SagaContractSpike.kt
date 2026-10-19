package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.domain.saga.CompensationArmOutcome
import dev.rubentxu.pipeline.v2.domain.saga.CompensationDeclaration
import dev.rubentxu.pipeline.v2.domain.saga.CompensationId
import dev.rubentxu.pipeline.v2.domain.saga.CompensationKey
import dev.rubentxu.pipeline.v2.domain.saga.CompensationKind
import dev.rubentxu.pipeline.v2.domain.saga.CompensationOperations
import dev.rubentxu.pipeline.v2.domain.saga.CompensationPolicy
import dev.rubentxu.pipeline.v2.domain.saga.EffectEvidence
import dev.rubentxu.pipeline.v2.domain.saga.ResourceRef
import dev.rubentxu.pipeline.v2.domain.saga.SAGA_COMPENSATION_CAPABILITY
import dev.rubentxu.pipeline.v2.domain.saga.SagaActionId
import dev.rubentxu.pipeline.v2.domain.saga.SagaDecider
import dev.rubentxu.pipeline.v2.domain.saga.SagaId
import dev.rubentxu.pipeline.v2.domain.saga.SagaPlanFingerprint
import dev.rubentxu.pipeline.v2.domain.step.StepCapability

/**
 * PG-0 — the viability spike, compiled against the PUBLISHED SDK of a real RC.
 *
 * ## What this file is
 *
 * A probe. It exists to answer ONE question with a compiler instead of with an opinion:
 *
 * > Can a repository that has never seen PipelineK's source use the Saga contract?
 *
 * The answer it has to be true for is not "the types are visible". It is: **the contract is
 * SUFFICIENT** for an external consumer to declare a compensation, ask the engine what it
 * means, and read back what was promised — without importing a single internal class.
 *
 * ## What it deliberately does NOT do
 *
 * It does not implement a Saga, and it does not compensate anything. The engine owns the
 * compensation, and a spike that starts doing the engine's work would be a second engine.
 *
 * It also does not reach for anything that is not published. There is no import of the
 * durable store, of the event store or of the operation journal in this file, and there
 * cannot be: those are not published contracts, so a consumer that reached for them would
 * not compile. That absence is the guarantee, not an accident of the spike.
 */
object SagaContractSpike {

    /**
     * The capability an external Saga Step declares.
     *
     * The whole seam, in one key: the host provides the port behind it and the plugin asks
     * for it. A consumer that never asks for it cannot make a promise, and a consumer that
     * asks for it cannot get anything else.
     */
    val REQUIRED_CAPABILITY: StepCapability = SAGA_COMPENSATION_CAPABILITY

    /**
     * Declares the compensation this plugin wants to be able to run.
     *
     * The declaration carries no runtime handle, no lambda and no secret: only the data that
     * has to survive until a later process compensates.
     */
    fun declarationFor(
        sagaId: String,
        actionId: String,
        compensationId: String,
        resourceRef: String,
        planFingerprint: String,
    ): CompensationDeclaration = CompensationDeclaration(
        sagaId = SagaId.of(sagaId),
        actionId = SagaActionId.of(actionId),
        compensationId = CompensationId.of(compensationId),
        planFingerprint = SagaPlanFingerprint(planFingerprint),
        resourceRef = ResourceRef.of(resourceRef),
        kind = CompensationKind.RESTORE,
    )

    /**
     * Asks the engine to promise the compensation, and tells the caller what happened.
     *
     * The three outcomes the caller has to distinguish, and they are not interchangeable:
     * a promise made, the same promise asked for again, and a REFUSAL. A consumer that
     * treated a refusal as a success would run a forward effect behind a promise nobody
     * kept.
     */
    fun promise(
        operations: CompensationOperations,
        declaration: CompensationDeclaration,
    ): PromiseOutcome = when (val outcome = operations.arm(declaration)) {
        is CompensationArmOutcome.Armed -> PromiseOutcome.Promised(outcome.obligation.key)
        is CompensationArmOutcome.AlreadyArmed -> PromiseOutcome.AlreadyPromised(outcome.obligation.key)
        is CompensationArmOutcome.PlanDivergence -> PromiseOutcome.ConflictingPlan(outcome.existing.key)
        is CompensationArmOutcome.Rejected -> PromiseOutcome.Refused(outcome.reason)
    }

    /**
     * Reads back what was promised, and asks the engine what the evidence means.
     *
     * The decision is the ENGINE's, not the plugin's: the plugin reports what it observed
     * and the engine applies the rule. A plugin that re-implemented the rule would be a
     * second, divergent copy of it.
     */
    fun consult(
        operations: CompensationOperations,
        key: CompensationKey,
        evidence: EffectEvidence,
    ): SagaConsultation {
        val obligation = operations.read(key)
        val decision = SagaDecider.decide(
            obligation = obligation,
            evidence = evidence,
            policy = CompensationPolicy.NotReconcilable,
        )
        return SagaConsultation(obligation = obligation, decision = decision)
    }

    /** What a promise attempt means to the caller. */
    sealed interface PromiseOutcome {
        /** The promise was made and is durable. */
        data class Promised(val key: CompensationKey) : PromiseOutcome

        /** The same promise was already there. */
        data class AlreadyPromised(val key: CompensationKey) : PromiseOutcome

        /** The key is already armed under a different plan, and the first promise is kept. */
        data class ConflictingPlan(val key: CompensationKey) : PromiseOutcome

        /** No promise was made. The forward effect must not run. */
        data class Refused(val reason: dev.rubentxu.pipeline.v2.domain.saga.ArmRejection) : PromiseOutcome
    }

    /** What the engine answered when asked about an obligation. */
    data class SagaConsultation(
        val obligation: dev.rubentxu.pipeline.v2.domain.saga.CompensationObligation?,
        val decision: dev.rubentxu.pipeline.v2.domain.saga.SagaDecision,
    )
}
