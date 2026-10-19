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
gh release download v0.66.0-rc1 -R Rubentxu/pipeline-kotlin -p 'pipelinek-0.66.0.sdk.zip' -D /tmp/sdkdl
unzip pipelinek-0.66.0.sdk.zip -d /tmp/sdk
./gradlew compileKotlin -PsdkRepo=/tmp/sdk -PsdkVersion=0.66.0
```

The version is always explicit. A default could only be one that fails to resolve, and that
failure arrives far from the line that matters.

## State

| Milestone | What | State |
|---|---|---|
| PG-0 | Viability spike: does the published contract suffice for an external consumer? | **done** — `SagaContractSpike`, `SagaPlanSpike` and `SagaRuntimeSpike` compile against the published SDK of `v0.66.0-rc1` |
| PG-1 | First real milestone: the Saga Step | not started — needs PK-1 **and** PK-2 of the Core |
| PG-2 | Compensation body | not started |

PG-0 is a spike, not a milestone: it answers one question with a compiler instead of with an
opinion. The question is whether the contract an external consumer needs is actually
published, and the answer it found is yes — the whole Saga contract is inside the published
`pipeline-domain`, so this repository needs exactly one published artifact to compile against.

### What PG-0 actually proved

- **Producer**: PipelineK `v0.66.0-rc1` (tag commit `3778e3adae3a8155438ecbd944a028f9350a58b3`,
  release candidate `sha256:709fb3dd385f6b45fc9b92d1e29d0f01c142015d6288c9f27064e59a59cff0d3`).
- **Artifact consumed**: `pipelinek-0.66.0.sdk.zip`, sha256
  `13b26174babbb6c4f44b3047fcc3fa4214cf825bd94c23e116badc8279ad2d89`, verified against the
  release's `SHA256SUMS`.
- **Resolved classpath**: exactly one PipelineK artifact — `pipeline-domain:0.66.0`. Everything
  else on the classpath is the Kotlin standard library and the JSON layer the producer itself
  declares as `api`. Nothing internal is reachable, because nothing internal is published.
- **The spike is a real probe**: its first compile **failed** with an unresolved reference
  (`CompensationId`). That failure is the point of doing the spike with a compiler instead of
  with an opinion — and it found a gap in the consumer, not in the contract.
- **The third probe is real too**: `SagaRuntimeSpike.kt` compiles against `0.66.0` and **fails**
  against `0.65.0` with unresolved references to `SagaRuntime`, `SagaPlan`, `SagaActionPlan`,
  `SagaCompensationReport` and `SAGA_RUNTIME_CAPABILITY`. A probe that compiled against
  everything would prove nothing about the contract it claims to use.

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

Two things it still does **not** bring, and that is why PG-1 is still blocked:

- The Core does not yet demonstrate the loop **end to end**: the interpreter exists and is
  exercised against a real durable store, but the demonstration with a real plugin, a real
  process and real resources — and the reconciliation of the crash windows — is still to come.
- A slot is addressed by the plugin step key of the child, so two children of the **same** kind
  collide and the seam refuses the ambiguous name instead of guessing. A Saga plugin that wants
  two bodies of its own kind declares two definitions.

So the door is open; the demonstration behind it is not built yet. That is the Core's next
milestone, and this repository waits for it.
