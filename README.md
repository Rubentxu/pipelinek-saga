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
gh release download v0.65.0-rc1 -R Rubentxu/pipeline-kotlin -p 'pipelinek-0.65.0.sdk.zip' -D /tmp/sdkdl
unzip pipelinek-0.65.0.sdk.zip -d /tmp/sdk
./gradlew compileKotlin -PsdkRepo=/tmp/sdk -PsdkVersion=0.65.0
```

The version is always explicit. A default could only be one that fails to resolve, and that
failure arrives far from the line that matters.

## State

| Milestone | What | State |
|---|---|---|
| PG-0 | Viability spike: does the published contract suffice for an external consumer? | **done** — `src/main/kotlin/dev/rubentxu/pipeline/v2/saga/spike/SagaContractSpike.kt` and `SagaPlanSpike.kt` compile against the published SDK of `v0.65.0-rc1` |
| PG-1 | First real milestone: the Saga Step | not started — needs PK-1 **and** PK-2 of the Core |
| PG-2 | Compensation body | not started |

PG-0 is a spike, not a milestone: it answers one question with a compiler instead of with an
opinion. The question is whether the contract an external consumer needs is actually
published, and the answer it found is yes — the whole Saga contract is inside the published
`pipeline-domain`, so this repository needs exactly one published artifact to compile against.

### What PG-0 actually proved

- **Producer**: PipelineK `v0.65.0-rc1` (tag commit `5c318772cde9bc1e4ef6360407bf06633102c3f4`,
  release candidate `sha256:907e725ab432cd3f94777f6ffefa221b9e61887655ed5e7f3e0486d91a9293e6`).
- **Artifact consumed**: `pipelinek-0.65.0.sdk.zip`, sha256
  `2322dcf723bf8292ade1a9657ae5c7d7cfba45e5f3a7f77711bbcb764647e2e1`, verified against the
  release's `SHA256SUMS`.
- **Resolved classpath**: exactly one PipelineK artifact — `pipeline-domain:0.65.0`. Everything
  else on the classpath is the Kotlin standard library and the JSON layer the producer itself
  declares as `api`. Nothing internal is reachable, because nothing internal is published.
- **The spike is a real probe**: its first compile **failed** with an unresolved reference
  (`CompensationId`). That failure is the point of doing the spike with a compiler instead of
  with an opinion — and it found a gap in the consumer, not in the contract.
- **The second probe is real too**: `SagaPlanSpike.kt` compiles against `0.65.0` and **fails**
  against `0.64.0` with unresolved references to `SagaPlanOperations`, `SAGA_PLAN_CAPABILITY`,
  `CompensationDirective`, `CompensationObservation` and `SagaRecordOutcome`. A probe that
  compiled against everything would prove nothing about the contract it claims to use.

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

What it means for a consumer is concrete: a plugin can now declare that a call is about to
leave the process, report what it turned out to be, and ask what the Saga owes — and the
answer is the SAME one the engine would give, because it is derived from the durable state and
from a published function, not from a copy of the rule living in the plugin.

Two things it still does **not** bring, and that is why PG-1 is still blocked:

- The Core does not yet **execute** a Saga's compensation: the decision and the durable
  lifecycle exist, but nothing yet dispatches the compensation body through the same
  dispatcher, and the crash windows are not reconciled yet.
- A slot is addressed by the plugin step key of the child, so two children of the **same** kind
  collide and the seam refuses the ambiguous name instead of guessing. A Saga plugin that wants
  two bodies of its own kind declares two definitions.

So the door is open; the interpreter behind it is not built yet. That is the Core's next
milestone, and this repository waits for it.
