package dev.rubentxu.pipeline.v2.saga.spike

import dev.rubentxu.pipeline.v2.events.CompensationFinished
import dev.rubentxu.pipeline.v2.events.CompensationStarted
import dev.rubentxu.pipeline.v2.events.DomainEvent
import dev.rubentxu.pipeline.v2.events.SagaTerminal

/**
 * The fifth probe: the Saga's story, read from the EVENT PLANE.
 *
 * ## What it adds to the first four
 *
 * The first four probes asked what a Saga PLEDGES, what it OWES, how it is COMPENSATED and what
 * its refusals MEAN. This one asks the question a consumer of a finished run actually has:
 * **what happened, and does it need a human?**
 *
 * The answer comes from the events, not from the durable rows. That is the point of the probe:
 * an operator, an inspector or another service must be able to read what the compensation loop
 * did from the log of the run, without opening SQLite and without knowing anything about the
 * engine's internals. A Saga that is only observable in a private table is a Saga that is only
 * observable by the engine.
 *
 * ## What it deliberately does NOT do
 *
 * It does not decide, and it does not compensate. It reads facts that the ENGINE announced and
 * turns them into answers. The engine owns the state; this owns the reading.
 */
object SagaEventSpike {

    /**
     * The compensation bodies that ran, in the order they started.
     *
     * The SLOT is what it reports, because the slot is the join with the journal: it names the
     * body that undid each effect.
     */
    fun compensatedSlots(events: Sequence<DomainEvent>): List<String> =
        events.filterIsInstance<CompensationStarted>().map { it.slot }.toList()

    /**
     * How each compensation ended, in the order they finished.
     *
     * The three outcomes are not interchangeable: `CONFIRMED` means the resource is back,
     * `FAILED` means it is not, and `UNCERTAIN` means nobody knows.
     */
    fun outcomes(events: Sequence<DomainEvent>): List<String> =
        events.filterIsInstance<CompensationFinished>().map { it.outcome }.toList()

    /**
     * The state the Saga ended in, as the event plane says it.
     *
     * `null` when the run has no terminal Saga event: the loop may not have started, or the
     * process may have died before closing it. That is not the same as "settled", and the
     * difference is the whole reason this reads the event instead of assuming.
     */
    fun terminalState(events: Sequence<DomainEvent>): String? =
        events.filterIsInstance<SagaTerminal>().lastOrNull()?.state

    /**
     * Whether the Saga ended needing a human, and why.
     *
     * A Saga that was compensated is a Saga that recovered from a failure: it does NOT make the
     * failed run a successful one, and this probe says nothing that could be read that way.
     */
    fun interventionReason(events: Sequence<DomainEvent>): String? =
        events.filterIsInstance<SagaTerminal>().lastOrNull()?.reason

    /**
     * The actions the Saga compensated, in the order it compensated them.
     */
    fun compensatedActions(events: Sequence<DomainEvent>): List<String> =
        events.filterIsInstance<SagaTerminal>().lastOrNull()?.compensatedActions.orEmpty()
}
