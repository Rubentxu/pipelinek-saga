package dev.rubentxu.pipeline.v2.saga.contract

import dev.rubentxu.pipeline.v2.domain.PluginStepId
import dev.rubentxu.pipeline.v2.domain.saga.CompensationKind
import dev.rubentxu.pipeline.v2.domain.saga.CompensationId
import dev.rubentxu.pipeline.v2.domain.saga.ResourceRef
import dev.rubentxu.pipeline.v2.domain.saga.SagaActionId
import dev.rubentxu.pipeline.v2.domain.saga.SagaActionPlan
import dev.rubentxu.pipeline.v2.domain.saga.SagaId
import dev.rubentxu.pipeline.v2.domain.saga.SagaPlanFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Locale

/**
 * The CONTRACT test of the canonical form of a compensation plan.
 *
 * ## What this test is for
 *
 * A Saga's plan fingerprint is the engine's memory of a promise. The durable obligation
 * carries the DIGEST, not the bodies, so the digest is the only thing that lets a later
 * process tell "the same promise" from "the same key with another plan under it".
 *
 * That only works if everyone derives the digest the same way. Until the form was published,
 * it was not published: the SDK handed consumers the fingerprint TYPE and no form to derive
 * it from, so every consumer had to invent one. Two implementations inside the producer
 * already did, and they disagreed — one hashed the plan WITH the bodies and one WITHOUT
 * them, both under the same version marker.
 *
 * This repository is the other side of that contract, and this test is where the agreement
 * is CHECKED rather than hoped for.
 *
 * ## Why the implementation below is NOT the SDK's
 *
 * The test computes the digest with its own implementation of the published form, and then
 * compares it with the SDK's. If it called the SDK's `compute`, the test would compare the
 * SDK with itself and would pass whatever the SDK did — which is the mistake a contract test
 * exists to prevent.
 *
 * The implementation below is written from the published specification: the version, the
 * eight fields in their order, the `|` separator, SHA-256 over the UTF-8 bytes, lowercase
 * hexadecimal, and the locale pinned to `Locale.ROOT`. It is the same text an external
 * implementer would read.
 */
class SagaPlanFingerprintContractTest {

    @Test
    @DisplayName("this repository implements the form the SDK says it implements")
    fun `the version agrees`() {
        assertEquals(
            SagaPlanFingerprint.CANONICAL_FORM_VERSION,
            FORM_VERSION,
            "the SDK publishes version '${SagaPlanFingerprint.CANONICAL_FORM_VERSION}' and this " +
                "repository implements '$FORM_VERSION'. The form changed: implement the new one, " +
                "or the engine will read this repository's promise as a different plan.",
        )
    }

    @Test
    @DisplayName("the same plan produces the same fingerprint in both implementations")
    fun `the digest agrees`() {
        PLANS.forEach { (name, plan) ->
            assertEquals(
                SagaPlanFingerprint.compute(plan.sagaId, plan.actionPlan).hex,
                digestOf(canonicalFormOf(plan)),
                "the plan '$name' hashes differently in the two implementations",
            )
        }
    }

    @Test
    @DisplayName("the canonical TEXT agrees, so a mismatch says WHICH field moved")
    fun `the canonical form agrees`() {
        PLANS.forEach { (name, plan) ->
            assertEquals(
                SagaPlanFingerprint.canonicalFormOf(plan.sagaId, plan.actionPlan),
                canonicalFormOf(plan),
                "the plan '$name' is spelled differently in the two implementations",
            )
        }
    }

    @Test
    @DisplayName("a different plan produces a different fingerprint")
    fun `different plans differ`() {
        val digests = PLANS.map { (name, plan) -> name to digestOf(canonicalFormOf(plan)) }.toMap()

        assertEquals(
            digests.size,
            digests.values.toSet().size,
            "two different plans hashed the same: $digests",
        )
    }

    @Test
    @DisplayName("the bodies are part of the plan, so a different body is a different promise")
    fun `the bodies are part of the plan`() {
        val base = PLANS.first().second
        val other = base.copy(
            actionPlan = base.actionPlan.copy(compensateSlot = PluginStepId("another-body")),
        )

        assertNotEquals(
            SagaPlanFingerprint.compute(base.sagaId, base.actionPlan),
            SagaPlanFingerprint.compute(other.sagaId, other.actionPlan),
        )
    }

    @Test
    @DisplayName("the digest is 64 lowercase hexadecimal characters")
    fun `the digest has the published shape`() {
        val hex = digestOf(canonicalFormOf(PLANS.first().second))

        assertEquals(64, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' }, "not lowercase hex: $hex")
    }

    // --- this repository's own implementation of the published form -------

    /**
     * The version this repository implements.
     *
     * It is written HERE, on purpose, and not read from the SDK: the test that says "we
     * implement the same form" is only worth something if the two sides are two sides.
     */
    private val FORM_VERSION: String = "saga-v1"

    /**
     * The canonical form, implemented from the published specification.
     *
     * Eight fields, in this order, separated by `|`:
     *
     * ```text
     * <version>|<sagaId>|<actionId>|<compensationId>|<resourceRef>|<forwardSlot>|<compensateSlot>|<kind>
     * ```
     */
    private fun canonicalFormOf(plan: PlanUnderTest): String = listOf(
        FORM_VERSION,
        plan.sagaId.value,
        plan.actionPlan.actionId.value,
        plan.actionPlan.compensationId.value,
        plan.actionPlan.resourceRef.value,
        plan.actionPlan.forwardSlot.value,
        plan.actionPlan.compensateSlot.value,
        plan.actionPlan.kind.name,
    ).joinToString("|")

    /**
     * SHA-256 over the UTF-8 bytes of the canonical form, in lowercase hexadecimal.
     *
     * The locale is pinned to [Locale.ROOT], because the published specification pins it
     * too: the rendering of a digest must not depend on the machine that computes it.
     * (Kotlin's `String.format` extension takes the locale as its SECOND argument, so
     * `"%02x".format(Locale.ROOT, byte)` is the locale-aware call — the first argument
     * is the format string and the locale never lands in a `%s` hole.)
     */
    private fun digestOf(canonical: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }

    private data class PlanUnderTest(
        val sagaId: SagaId,
        val actionPlan: SagaActionPlan,
    )

    private companion object {
        /**
         * The plans the two implementations are compared on.
         *
         * The first one is a REAL plan: the `reserve` action of the producer's demo chain,
         * whose digest the producer's own durable store holds. The second one exists to
         * catch a form that only happens to work for one shape.
         */
        val PLANS: List<Pair<String, PlanUnderTest>> = listOf(
            "reserve" to PlanUnderTest(
                sagaId = SagaId.of("checkout"),
                actionPlan = SagaActionPlan(
                    actionId = SagaActionId.of("reserve"),
                    compensationId = CompensationId.of("release"),
                    resourceRef = ResourceRef.of("stock/42"),
                    kind = CompensationKind.RESTORE,
                    forwardSlot = PluginStepId("example.saga.reserve.forward"),
                    compensateSlot = PluginStepId("example.saga.reserve.compensate"),
                ),
            ),
            "charge" to PlanUnderTest(
                sagaId = SagaId.of("checkout"),
                actionPlan = SagaActionPlan(
                    actionId = SagaActionId.of("charge"),
                    compensationId = CompensationId.of("refund"),
                    resourceRef = ResourceRef.of("payment/42"),
                    kind = CompensationKind.RESTORE,
                    forwardSlot = PluginStepId("example.saga.charge.forward"),
                    compensateSlot = PluginStepId("example.saga.charge.compensate"),
                ),
            ),
            "delete-kind" to PlanUnderTest(
                sagaId = SagaId.of("cleanup"),
                actionPlan = SagaActionPlan(
                    actionId = SagaActionId.of("drop"),
                    compensationId = CompensationId.of("undrop"),
                    resourceRef = ResourceRef.of("bucket/tmp"),
                    kind = CompensationKind.DELETE,
                    forwardSlot = PluginStepId("example.saga.drop.forward"),
                    compensateSlot = PluginStepId("example.saga.drop.compensate"),
                ),
            ),
        )
    }
}
