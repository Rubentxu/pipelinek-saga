package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.domain.RunOutcome
import dev.rubentxu.pipeline.v2.domain.saga.RunPresentation
import dev.rubentxu.pipeline.v2.domain.saga.SagaOutcome
import dev.rubentxu.pipeline.v2.events.DomainEvent
import dev.rubentxu.pipeline.v2.events.identity.RunPresentationProjection

/**
 * The sixth probe: the run's VERDICT, read from the event plane with the published projection.
 *
 * ## What it adds to the fifth
 *
 * The fifth probe read the plane BY HAND: it filtered `CompensationStarted`,
 * `CompensationFinished` and `SagaTerminal` and answered "which bodies ran, how did they end,
 * and does a human have to decide". That answer is right, but it is the CONSUMER doing the
 * engine's bookkeeping, and every consumer that asks the same question has to redo it —
 * correctly, and with the same judgement about what an absent event means.
 *
 * From 0.74.0 the SDK answers it once and publishes the answer: `RunPresentationProjection` reads
 * the plane and returns a typed `RunPresentation`. This probe is the consumer's side of that:
 * one call, one typed fact, and no correlation of two events by hand.
 *
 * ## What it adds to the fifth, in facts
 *
 * The fifth probe could only say what happened to the SAGA. This one says what happened to the
 * RUN and to the SAGAS together, and it says it in the vocabulary of the domain:
 *
 *  - the run's outcome, typed, with the failure it carries (kind and message);
 *  - what each Saga left behind, named, with the reason when a human is needed;
 *  - whether a human has work at all, and whether the run's story is over.
 *
 * ## What it deliberately does NOT do
 *
 * It does not decide anything. The engine decided; the projection reads; this renders. A consumer
 * that wanted to decide would have to re-derive the outcome from raw words, which is exactly the
 * duplication the published projection removes.
 */
object SagaVerdictSpike {

    /**
     * The typed verdict of a run, read from its plane.
     *
     * @param runId the run to present.
     * @param events the plane of that run, in the order the store returned it.
     */
    fun verdict(runId: String, events: Sequence<DomainEvent>): RunPresentation =
        RunPresentationProjection.read(runId, events.asIterable())

    /**
     * The verdict, in the words an operator reads.
     *
     * One line for the run and one line per Saga, because the two questions are different: how
     * the run went, and what the Sagas left behind.
     */
    fun describe(presentation: RunPresentation): String = buildString {
        // Bound to a local: a property from another module cannot be smart-cast.
        val outcome = presentation.outcome
        append("run: ").append(outcomeOf(outcome))
        if (outcome is RunOutcome.Failure) {
            append(" (").append(outcome.failure.kind).append(")")
            append(" - ").append(outcome.failure.message)
        }
        presentation.sagas.forEach { saga ->
            append("\n  saga [").append(sagaIdOf(saga)).append("]: ")
            when (saga) {
                is SagaOutcome.Settled -> append("SETTLED")
                is SagaOutcome.NeedsIntervention ->
                    append("NEEDS_INTERVENTION (").append(saga.reason.name).append(")")
            }
        }
        if (presentation.needsIntervention) {
            append("\n  a human has to decide")
        }
    }

    /** How the run went, in the words the domain uses. */
    private fun outcomeOf(outcome: RunOutcome): String = when (outcome) {
        RunOutcome.Success -> "success"
        RunOutcome.Unstable -> "unstable"
        RunOutcome.Aborted -> "aborted"
        is RunOutcome.Failure -> "failure"
    }

    /** Which Saga a verdict is about. */
    private fun sagaIdOf(saga: SagaOutcome): String = when (saga) {
        is SagaOutcome.Settled -> saga.sagaId
        is SagaOutcome.NeedsIntervention -> saga.sagaId
    }
}
