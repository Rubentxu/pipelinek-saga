package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.domain.saga.CompensationDirective
import dev.rubentxu.pipeline.v2.domain.saga.CompensationId
import dev.rubentxu.pipeline.v2.domain.saga.CompensationKind
import dev.rubentxu.pipeline.v2.domain.saga.CompensationPolicy
import dev.rubentxu.pipeline.v2.domain.saga.ResourceRef
import dev.rubentxu.pipeline.v2.domain.saga.SAGA_RUNTIME_CAPABILITY
import dev.rubentxu.pipeline.v2.domain.saga.SagaActionId
import dev.rubentxu.pipeline.v2.domain.saga.SagaActionPlan
import dev.rubentxu.pipeline.v2.domain.saga.SagaCompensationReport
import dev.rubentxu.pipeline.v2.domain.saga.SagaId
import dev.rubentxu.pipeline.v2.domain.saga.SagaPlan
import dev.rubentxu.pipeline.v2.domain.saga.SagaRuntime
import dev.rubentxu.pipeline.v2.domain.step.StepCapability

/**
 * The third probe: the RUNTIME port, as an external consumer sees it.
 *
 * ## What it adds to the first two
 *
 * The first probe declared a promise; the second walked its lifecycle. This one hands the
 * ENGINE a PLAN and lets the engine run the compensation loop: which obligation is owed, in
 * which order, and which body undoes it are all the engine's answers, derived from durable
 * state this repository cannot see.
 *
 * ## What it deliberately does NOT do
 *
 * It does not decide the order, skip an obligation, or mark a compensation as done without
 * the engine having dispatched the body. A consumer that could do those things would be a
 * second engine — and a second engine is exactly how a Saga undoes something twice.
 *
 * ## The plan is DATA
 *
 * The plan crosses the boundary as plain values: identities, references, and the keys of the
 * bodies that implement each phase. No closure, no handle, no secret.
 */
object SagaRuntimeSpike {

    /**
     * The capability an external Saga Step declares to ask the engine to compensate.
     */
    val REQUIRED_CAPABILITY: StepCapability = SAGA_RUNTIME_CAPABILITY

    /**
     * Declares the Saga this plugin wants to be able to compensate.
     *
     * The order of [actions] IS the order of the Saga: the arm order follows it and the
     * compensation order is its reverse.
     */
    fun plan(
        sagaId: String,
        policy: CompensationPolicy,
        actions: List<SagaActionPlan>,
    ): SagaPlan = SagaPlan(
        sagaId = SagaId.of(sagaId),
        policy = policy,
        actions = actions,
    )

    /** Declares one action of the Saga and the bodies that implement it. */
    fun action(
        actionId: String,
        compensationId: String,
        resourceRef: String,
        kind: CompensationKind,
        forwardSlot: String,
        compensateSlot: String,
    ): SagaActionPlan = SagaActionPlan(
        actionId = SagaActionId.of(actionId),
        compensationId = CompensationId.of(compensationId),
        resourceRef = ResourceRef.of(resourceRef),
        kind = kind,
        forwardSlot = dev.rubentxu.pipeline.v2.domain.PluginStepId(forwardSlot),
        compensateSlot = dev.rubentxu.pipeline.v2.domain.PluginStepId(compensateSlot),
    )

    /**
     * Hands the plan to the engine and waits for the loop to finish.
     *
     * The report says WHICH actions were compensated and IN WHICH ORDER, and what the Saga's
     * state is afterwards: settled, or needing a human.
     */
    suspend fun compensate(
        runtime: SagaRuntime,
        plan: SagaPlan,
    ): SagaCompensationReport = runtime.compensate(plan)

    /** Whether the Saga ended settled, as the report says it. */
    fun isSettled(report: SagaCompensationReport): Boolean =
        report.directive is CompensationDirective.Settled
}
