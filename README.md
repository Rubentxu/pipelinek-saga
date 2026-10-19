# pipelinek-saga

The Saga plugin for [PipelineK](https://github.com/Rubentxu/pipeline-kotlin): an **external
consumer** of PipelineK's published SDK.

It is a separate repository on purpose. PipelineK is the producer of the SDK; this repository
is a consumer of it, and the only thing the two share is the published contract. That is the
whole point of the split: a plugin that could reach into the engine's internals would not be
a plugin, it would be a fork wearing a plugin's name.

## The rules this repository lives by

1. **Published contracts only.** Everything here compiles against the artifacts a PipelineK
   release publishes. No import of an internal class, ever — and if a class is not published,
   the answer is to publish the contract, not to reach around the SDK.
2. **The engine owns the compensation.** The plugin declares an intent and reads back what
   the engine decided. It does not implement the compensation, and it does not decide what the
   engine's rules mean.
3. **No secrets, no lambdas, no runtime handles** in anything that crosses the boundary. What
   crosses is data: identities, references, fingerprints.
4. **One milestone at a time**, and the milestone that is open is named below.

## How the SDK reaches this repository

PipelineK does **not** publish its contracts to Maven Central. A release publishes the SDK
bundle as an asset (`pipelinek-<train>.sdk.zip`); download it from the release, unzip it and
point the build at the directory:

```bash
gh release download v0.68.0-rc1 -R Rubentxu/pipeline-kotlin -p 'pipelinek-0.68.0.sdk.zip' -D /tmp/sdkdl
unzip pipelinek-0.68.0.sdk.zip -d /tmp/sdk
./gradlew compileKotlin -PsdkRepo=/tmp/sdk -PsdkVersion=0.68.0
```

The version is always explicit. A default could only be one that fails to resolve, and that
failure arrives far from the line that matters.

## State

| Milestone | What | State |
|---|---|---|
| PG-0 | Viability spike: does the published contract suffice for an external consumer? | **done** — `SagaContractSpike`, `SagaPlanSpike`, `SagaRuntimeSpike`, `SagaRefusalSpike` and `SagaEventSpike` compile against the published SDK of `v0.68.0-rc1` |
| PG-1 | First real milestone: the Saga Step | not started — needs PK-1 **and** PK-2 of the Core |
| PG-2 | Compensation body | not started |

PG-0 is a spike, not a milestone: it answers one question with a compiler instead of with an
opinion. The question is whether the contract an external consumer needs is actually
published, and the answer it found is yes — the whole Saga contract is inside the published
`pipeline-domain`, so this repository needs exactly one published artifact to compile against.

### What PG-0 actually proved

- **Producer**: PipelineK `v0.68.0-rc1` (tag commit `347faf941f5f116c46df8115a9747d7da68b396b`,
  release candidate `sha256:43c39a23447fbc5fa1d055eaf89be5f9bc1c41555f6b3fb5a55a52d96ef630a1`).
- **Artifact consumed**: `pipelinek-0.68.0.sdk.zip`, sha256
  `ced40f0a7fabfa5b23d8b7efa5987e12f2469b8c5f2e43618c78fbc8ffec52a5`, verified against the
  release's `SHA256SUMS`.
- **Resolved classpath**: two PipelineK artifacts — `pipeline-domain:0.68.0` and
  `pipeline-events:0.68.0` (which brings `pipeline-scripting-api:0.68.0` with it). Everything
  else on the classpath is the Kotlin standard library. Nothing internal is reachable, because
  nothing internal is published. The second artifact is not a widening of the seam: the domain
  carries the CONTRACT and the events carry the OBSERVABLE FACTS of it, and a consumer that
  wants to read what a compensation loop did needs both.
- **The spike is a real probe**: its first compile **failed** with an unresolved reference
  (`CompensationId`). That failure is the point of doing the spike with a compiler instead of
  with an opinion — and it found a gap in the consumer, not in the contract.
- **The third probe is real too**: `SagaRuntimeSpike.kt` compiles against `0.66.0` and **fails**
  against `0.65.0` with unresolved references to `SagaRuntime`, `SagaPlan`, `SagaActionPlan`,
  `SagaCompensationReport` and `SAGA_RUNTIME_CAPABILITY`. A probe that compiled against
  everything would prove nothing about the contract it claims to use.
- **The fourth probe is a probe about refusals**: `SagaRefusalSpike.kt` compiles against `0.67.0`
  and **fails** against `0.66.0` with `Unresolved reference 'COMPENSATION_NOT_DISPATCHED'`. It
  exists because the two refusals a Saga can hit are not interchangeable: a compensation that was
  **not dispatched** left the resource untouched and may be retried, while one whose outcome is
  **uncertain** may already have been applied and must not be run again. A consumer that cannot
  tell them apart either undoes a resource twice or leaves the Saga stuck forever.
- **The fifth probe reads the event plane**: `SagaEventSpike.kt` compiles against `0.68.0` and
  **fails** against `0.67.0` with unresolved references to `CompensationStarted`,
  `CompensationFinished` and `SagaTerminal`. It answers the question a consumer of a FINISHED
  run really has — which bodies ran, how they ended, and whether a human is needed — from the
  events, without opening SQLite and without knowing anything about the engine's internals. A
  Saga that is only observable in a private table is a Saga that is only observable by the
  engine.

The first milestone that changes behaviour is PG-1, and it is blocked on the Core: the
compensation must be executed and its evidence persisted before a plugin can promise
something that the engine will honour.

### What the new SDK brought (and what it did not)

`v0.64.0-rc1` added the **slot seam** to the published contract: `BodySlotInvoker`, the port
that lets a handler run **one** named child of its own body instead of the whole body. That is
the piece a Saga needs to choose between a *forward* body and a *compensate* body.

`v0.65.0-rc1` adds the **plan seam**: `SagaPlanOperations` (with the `saga.plan` capability),
the durable obligation states a Saga passes through (`EFFECT_PENDING`, `EFFECT_UNKNOWN`,
`COMPENSATION_PENDING`, `COMPENSATION_FAILED`, `COMPENSATION_UNCERTAIN`), the pure decision
`SagaDecider.nextCompensation(snapshot, policy)` and the lifecycle table
`CompensationLifecycle` that says which state may follow which.

`v0.66.0-rc1` adds the **runtime seam**: `SagaRuntime` (with the `saga.runtime` capability),
`SagaPlan` and `SagaActionPlan` — the plan as DATA — and `SagaCompensationReport`, which says
which actions were compensated and IN WHICH ORDER. This is the split the whole contract rests
on: the plugin declares what its actions are and which body implements each phase; the engine
decides what is owed, in what order, and dispatches the bodies.

What it means for a consumer is concrete: a plugin can now hand over its plan and let the
ENGINE run the compensation loop, and the answer is the SAME one the engine would give,
because it is derived from the durable state and from a published function, not from a copy of
the rule living in the plugin.

`v0.67.0-rc1` is a smaller train, and it is the smallest useful one: the engine's compensation
loop now **obeys** the durable state instead of asking it and discarding the answer. A refusal
means the compensation was NOT dispatched — the resource was not touched — and the Saga stops
there. The published vocabulary grows with exactly one word for that fact,
`InterventionReason.COMPENSATION_NOT_DISPATCHED`, next to the `COMPENSATION_UNCERTAIN` that
names the case where something ran and nobody knows what it did. Two words, two different
answers to the only question that matters after a failure: may I try again?

`v0.68.0-rc1` makes the Saga OBSERVABLE from outside: the compensation loop announces what it
does — a compensation started, a compensation finished, the Saga's terminal state — and the
announcements are published events. The engine still owns the state and still produces every
fact; the consumer only reads them.

Two things it still does **not** bring, and that is why PG-1 is still blocked:

- The Core does not yet demonstrate the loop **end to end**: the interpreter exists and is
  exercised against a real durable store, but the demonstration with a real plugin, a real
  process and real resources — and the reconciliation of the crash windows — is still to come.
- A slot is addressed by the plugin step key of the child, so two children of the **same** kind
  collide and the seam refuses the ambiguous name instead of guessing. A Saga plugin that wants
  two bodies of its own kind declares two definitions.

So the door is open; the demonstration behind it is not built yet. That is the Core's next
milestone, and this repository waits for it.
