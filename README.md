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
gh release download v0.75.0-rc1 -R Rubentxu/pipeline-kotlin -p 'pipelinek-0.75.0.sdk.zip' -D /tmp/sdkdl
unzip pipelinek-0.75.0.sdk.zip -d /tmp/sdk
./gradlew compileKotlin -PsdkRepo=/tmp/sdk -PsdkVersion=0.75.0
```

The version is always explicit. A default could only be one that fails to resolve, and that
failure arrives far from the line that matters.

## State

| Milestone | What | State |
|---|---|---|
| PG-0 | Viability spike: does the published contract suffice for an external consumer? | **done** — `SagaContractSpike`, `SagaPlanSpike`, `SagaRuntimeSpike`, `SagaRefusalSpike`, `SagaEventSpike`, `SagaVerdictSpike` and `SagaLifecycleSpike` compile against the published SDK of `v0.75.0-rc1` |
| PG-1 | First real milestone: the Saga Step | not started — needs PK-1 **and** PK-2 of the Core |
| PG-2 | Compensation body | not started |

PG-0 is a spike, not a milestone: it answers one question with a compiler instead of with an
opinion. The question is whether the contract an external consumer needs is actually
published, and the answer it found is yes — the whole Saga contract is inside the published
`pipeline-domain`, so this repository needs exactly one published artifact to compile against.

### What PG-0 actually proved

- **Producer**: PipelineK `v0.75.0-rc1` (tag commit `3079b2603594ee92666929317f66224e42b308b4`,
  release candidate `sha256:e24abea8ed0ef01f8f70f8a2fcfbe8efce52b33d7d4b73acfd5c54cd1b4973fa`).
- **Artifact consumed**: `pipelinek-0.75.0.sdk.zip`, sha256
  `0055ef177d334e14b0f512b3c89adfd7d104e732c6212eef09c5bae5fb200362`, verified against the
  release's `SHA256SUMS`.
- **Resolved classpath**: two PipelineK artifacts — `pipeline-domain:0.75.0` and
  `pipeline-events:0.75.0` (which brings `pipeline-scripting-api:0.75.0` with it). Everything
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
- **The fifth probe reads the event plane**: `SagaEventSpike.kt` compiles against `0.70.0` (and
  against `0.68.0`, where the API arrived) and **fails** against `0.67.0` with unresolved references
  to `CompensationStarted`, `CompensationFinished` and `SagaTerminal` — re-verified in this cycle.
  It answers the question a consumer of a FINISHED run really has — which bodies ran, how they
  ended, and whether a human is needed — from the events, without opening SQLite and without
  knowing anything about the engine's internals. A Saga that is only observable in a private table
  is a Saga that is only observable by the engine.
- **The sixth probe reads the VERDICT, and it does not correlate anything**: `SagaVerdictSpike.kt`
  compiles against `0.74.0` and **fails** against `0.73.0` with unresolved references to
  `RunPresentationProjection`, `RunPresentation` and `SagaOutcome` — re-verified in this cycle.
  The fifth probe read the plane by hand, filtering `SagaTerminal` and reading its state word; this
  one asks the SDK for the whole answer — how the run went, what each Saga left behind, whether a
  human has work — in **one typed fact**. The difference is not stylistic: the fifth probe had to
  decide for itself what the absence of a terminal means, and every consumer would have had to
  decide it again, and could have decided it differently.
  The reading itself is proven in the producer, not here: its tests read the **real bytes** of two
  finished runs, captured from the durable store, and assert that the verdict they produce is the
  same one the product prints on stderr.
- **The seventh probe asks the engine to CLOSE a Saga**: `SagaLifecycleSpike.kt` compiles against
  `0.75.0` and **fails** against `0.74.0` with unresolved references to `SAGA_LIFECYCLE_CAPABILITY`,
  `SagaLifecycle` and `SagaCompletionReport` — re-verified in this cycle.
  It exists because until this train a Saga that had **worked** could not say so: the engine had one
  verb to end a Saga (`compensate`), and an obligation whose forward was confirmed stayed owed for
  ever, so a successful Saga compensated itself. Measured in the Core: a chain of two successful
  actions left the world as `reserve, charge, refund, release` while the run reported `SUCCESS`.
  The new port is the Saga's **lifecycle** — it EXTENDS the runtime port, so a consumer holds ONE
  handle with both ways of ending a Saga, and the SDK stays additive: the published interface did
  not grow an abstract method.

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

`v0.69.0-rc1`, `v0.70.0-rc1` and `v0.71.0-rc1` are the trains that turn that contract into a
**demonstration** and put the publication gate under test, and none of them changes the published
ABI: across those three, the five published artifacts carry **identical bytecode** — the only byte
that differs is the `Implementation-Version` stamped in their manifests. `v0.72.0-rc1` is the first
train that changes behaviour instead of demonstration, and `v0.73.0-rc1` is the first that grows
the published contract by even one name. What all five share is the property a consumer actually
depends on — the *contract* moves at its own pace, while the *product* around it moves fast.

- `v0.69.0-rc1` ships, inside the distribution, a **complete external Saga plugin** and a scenario
  that runs it: a real process, a real SQLite, three actions, the third one failing, and the engine
  compensating the first two in the inverse order over real resources. The plugin reaches the
  contract the way a plugin must — from outside, through published capabilities, with the engine
  owning the durable state.
- `v0.70.0-rc1` adds the other half of that experiment. The **run's verdict** now says what
  happened to its Saga (`saga [checkout]: SETTLED` / `saga [checkout]: NEEDS_INTERVENTION
  (UNKNOWN_EFFECT)`), because a Saga that leaves work for a human and a Saga that was fully
  compensated used to produce the same stderr, and they call for opposite responses. The same
  train also proves the other side with a second scenario: when the provider **cannot** promise
  that a failed call left no effect, the engine compensates **nothing** — not even what it knows —
  and the Saga ends in `NEEDS_INTERVENTION`. Compensating an older resource while a newer one is
  in doubt can destroy the state the newer compensation would have needed.
- `v0.71.0-rc1` adds **nothing** to the published contract — the same five artifacts, class for
  class — and repairs a hole in the **publication gate** itself. A bundled plugin whose manifest
  did not declare an `apiRange` used to **vanish** from the compatibility check instead of
  failing it, so the gate reported "compatible" about a plugin nobody had looked at, and the
  candidate was published. Now the *presence* of the manifest decides whether the bytes are a
  plugin at all, and its *content* decides what they declare: a manifest without a readable range
  is a **conflict**, not an absence, because nothing shows that plugin admitting the host. The
  failure sat on both ends of the seam — the host refused that plugin at admission and the
  producer never saw it. To a consumer of the SDK this train is invisible; to whoever installs
  the distribution it is the difference between a gate that means something and a gate that nods.

- `v0.72.0-rc1` changes the published **behaviour** without changing the published **shape**: of the
  five artifacts, exactly one class changes — `CompensationLifecycle`, in `pipeline-domain` — and
  not a single published signature moves (the ABI dump does not budge). It changes it because it
  was wrong. Re-writing a state an obligation already held was always allowed, so a process that
  came back after a crash could re-write "this call is on its way out" — which is not a retry, it
  is a **second authorisation** — and the effect happened twice, with a row that looked exactly
  like a first attempt. The states that are "on their way out" no longer accept a re-write, so a
  resumed run finds the call already authorised, stops, and says so:
  `NEEDS_INTERVENTION (UNKNOWN_EFFECT)` or `(COMPENSATION_UNCERTAIN)`. For a consumer the shape
  is the same; the guarantee is stronger, because a crash can no longer turn one effect into two.
  The proof is two scenarios that kill the process with `SIGKILL` at the exact point and resume it
  against the same store, with the world compared between the two runs.

- `v0.73.0-rc1` closes the other half of the crash story, and it does so with a **defect of its
  own** in the middle. The Core can now RECONCILE: when a provider declares that it recognises its
  own operation keys, the engine asks it again instead of handing the case to a human. Asking is
  only safe if the body can recognise the question — and it could not: the invocation's
  environment patch was projected onto the execution context only, while a `sh` process is
  launched with the process options and nothing else, so the key never reached the body and a
  re-dispatch could have repeated the effect. The fix projects a patch onto **both** surfaces, in
  one place, exactly the way the script's own scopes already did. The published contract grows by
  exactly one thing — the name of the environment variable by which a body receives the key of its
  operation (`SagaOperationIdentity.OPERATION_KEY_ENV`) — which is additive, and nothing else
  moves. For a consumer the shape is the same; the guarantee is stronger, because a lost answer
  can now be settled by **asking** instead of only by a human.

- `v0.74.0-rc1` publishes the **shape of the verdict** — the thing this repository was waiting for
  since the 3b-2. Until now the run's fate and the Saga's fate were two facts in two places: the
  word on the plane (`RunFinished`) and the terminal event (`SagaTerminal`), and a consumer that
  wanted to answer "did it fail, and is there work for a human?" had to correlate them by hand and
  to decide for itself what the absence of each one means. From this train the SDK answers it
  **once** and publishes the answer: `RunPresentationProjection` reads the plane and returns a
  typed `RunPresentation` — the run's outcome with its typed failure, what each Saga left behind
  with the reason when a human is needed, and whether the run's story is over. It fails closed, on
  purpose: no closing event reads as `Aborted` rather than as success, a word this build does not
  know reads as a failure rather than as a good run, and a Saga whose terminal cannot be read is
  never read as settled — including a Saga that armed an obligation and never said how it ended.
  The published contract grows additively: the verdict's ontology (`SagaOutcome`,
  `RunPresentation`, `mergeCompensationOutcome`), the vocabulary of the outcome
  (`RunOutcome.wireToken`) and the projection itself. The **event schema does not move**: the
  verdict is read from the facts already on the plane, not from a new field.
  For a consumer this is the difference between reading two events and reading one fact.

- `v0.75.0-rc1` publishes the **closing** of a Saga that succeeded. Until this train the
  contract had one way to end a Saga, and it was the wrong one for a happy path: an obligation
  whose forward was confirmed stayed owed for ever, so a Saga whose actions all succeeded was a
  Saga that had to compensate itself — the world said `reserve, charge, refund, release` while the
  run said `success`. The new port is the Saga's **lifecycle** (`SagaLifecycle`), and it EXTENDS
  the runtime port instead of replacing it: one handle with both endings, and a published
  interface that did not grow an abstract method. The domain gains the state that says the effect
  is KEPT (`EFFECT_KEPT`), the pure decision of whether a Saga may be closed (`SagaDecider
  .completion`) and the report that says which actions were kept (`SagaCompletionReport`).
  The engine discharges the promises in ONE durable statement, so a Saga is closed for good and a
  resumed run never finds half of it owed; and a closing the durable state cannot corroborate is
  REFUSED, with the obligation that blocks it and the reason — not with a silent success.

One thing it still does **not** bring, and that is why PG-1 is still blocked:

- A slot is addressed by the plugin step key of the child, so two children of the **same** kind
  collide and the seam refuses the ambiguous name instead of guessing. A Saga plugin that wants
  two bodies of its own kind declares two definitions. The shipped example needed six distinct
  kinds for a three-action chain, which is a property of the seam and not of the Saga — a real
  consumer should not have to borrow the core's kinds to get there.

The second one is gone: `v0.75.0-rc1` publishes the **closing** of a Saga that succeeded, and with
it the plane law that says a green run cannot have compensated. The consumer can now say which of
the two endings it wants, and the engine decides what that means for the durable state.

And what this repository is waiting for is no longer the shape of the verdict — that arrived with
`0.74.0-rc1` — nor the confirmation of a Saga that succeeded, which arrived with `0.75.0-rc1`, but
the direction of the slots. The crash windows and the reconciliation are closed: a crash no longer
repeats an effect, and a lost answer can be settled by asking the provider instead of only by a
human.
