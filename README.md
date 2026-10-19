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
gh release download v0.64.0-rc1 -R Rubentxu/pipeline-kotlin -p 'pipelinek-0.64.0.sdk.zip' -D /tmp/sdkdl
unzip pipelinek-0.64.0.sdk.zip -d /tmp/sdk
./gradlew compileKotlin -PsdkRepo=/tmp/sdk -PsdkVersion=0.64.0
```

The version is always explicit. A default could only be one that fails to resolve, and that
failure arrives far from the line that matters.

## State

| Milestone | What | State |
|---|---|---|
| PG-0 | Viability spike: does the published contract suffice for an external consumer? | **done** — `src/main/kotlin/dev/rubentxu/pipeline/v2/saga/spike/SagaContractSpike.kt` compiles against the published SDK of `v0.64.0-rc1` |
| PG-1 | First real milestone: the Saga Step | not started — needs PK-1 **and** PK-2 of the Core |
| PG-2 | Compensation body | not started |

PG-0 is a spike, not a milestone: it answers one question with a compiler instead of with an
opinion. The question is whether the contract an external consumer needs is actually
published, and the answer it found is yes — the whole Saga contract is inside the published
`pipeline-domain`, so this repository needs exactly one published artifact to compile against.

### What PG-0 actually proved

- **Producer**: PipelineK `v0.64.0-rc1` (tag commit `1f8ac46e6521285a7d0d3c40630abf4bd9d811b6`).
- **Artifact consumed**: `pipelinek-0.64.0.sdk.zip`, sha256
  `8e47665d15fec55df3faa0d5e9318005f10c40723302d0dfe3dbbea7a5ea8276`, verified against the
  release's `SHA256SUMS`.
- **Resolved classpath**: exactly one PipelineK artifact — `pipeline-domain:0.64.0`. Everything
  else on the classpath is the Kotlin standard library and the JSON layer the producer itself
  declares as `api`. Nothing internal is reachable, because nothing internal is published.
- **The spike is a real probe**: its first compile **failed** with an unresolved reference
  (`CompensationId`). That failure is the point of doing the spike with a compiler instead of
  with an opinion — and it found a gap in the consumer, not in the contract.

The first milestone that changes behaviour is PG-1, and it is blocked on the Core: the
compensation must be executed and its evidence persisted before a plugin can promise
something that the engine will honour.

### What the new SDK brought (and what it did not)

`v0.64.0-rc1` adds the **slot seam** to the published contract: `BodySlotInvoker`, the port
that lets a handler run **one** named child of its own body instead of the whole body.

That is the piece a Saga needs and did not have. Until now, a plugin could only run its body
WHOLE, so it could not choose between a *forward* body and a *compensate* body — which is
exactly the choice a Saga has to make. The seam is now published, and it is demonstrated by an
external plugin in the producer's own examples.

Two things it does **not** bring, and that is why PG-1 is still blocked:

- The Core does not yet **interpret** a Saga: there is no LIFO ordering of compensations, no
  persisted evidence of a compensation, and no reconciliation after a crash.
- A slot is addressed by the plugin step key of the child, so two children of the **same** kind
  collide and the seam refuses the ambiguous name instead of guessing. A Saga plugin that wants
  two bodies of its own kind declares two definitions.

So the door is open; the interpreter behind it is not built yet. That is the Core's next
milestone, and this repository waits for it.
