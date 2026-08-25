package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.persistence.AiOutboxPersistencePort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxClaim
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * outbox 저장소를 메모리로 대신하면서 조회 인자와 호출 순서를 남기는 fake.
 *
 * relay는 조회 조건과 확정 조건을 정확히 넘기는 것이 책임이라 결과뿐 아니라 인자와 순서도 관찰 대상이다.
 * 소유권 점유 시각은 실제 저장소처럼 밀리초로 자른다. relay가 자기 시계로 소유권을 다시 만들면 확정 조건이 어긋난다는 사실을 fake도 그대로 재현한다.
 */
class FakeAiOutboxPersistencePort : AiOutboxPersistencePort {
    val outboxes: MutableMap<AiOutboxId, AiOutbox> = linkedMapOf()

    val publishableCalls: MutableList<Pair<Instant, Int>> = mutableListOf()
    val staleCalls: MutableList<Pair<Instant, Int>> = mutableListOf()
    val statusCalls: MutableList<Pair<AiOutboxStatus, Int>> = mutableListOf()
    val claimCalls: MutableList<Triple<AiOutboxId, String, Instant>> = mutableListOf()
    val recoverCalls: MutableList<AiOutbox> = mutableListOf()
    val finalizeCalls: MutableList<Pair<AiOutbox, AiOutboxClaim>> = mutableListOf()
    val callOrder: MutableList<String> = mutableListOf()

    /** null이면 저장된 행을 발행 중으로 옮겨 돌려준다. */
    var claimResult: ((AiOutboxId) -> AiOutbox?)? = null
    var finalizeResult: Boolean = true

    /** false로 두면 저장된 상태와 무관하게 복구가 밀린다. 동시 복구를 재현하는 데 쓴다. */
    var recoverResult: Boolean = true

    /** 호출 순서별 확정 실패. null 자리는 그 호출이 성공한다는 뜻이고, 비면 모두 성공한다. */
    val finalizeFailures: ArrayDeque<Throwable?> = ArrayDeque()

    fun store(vararg outboxes: AiOutbox) {
        outboxes.forEach { this.outboxes[it.id] = it }
    }

    override suspend fun save(outbox: AiOutbox): AiOutbox {
        outboxes[outbox.id] = outbox

        return outbox
    }

    override suspend fun findById(id: AiOutboxId): AiOutbox? {
        return outboxes[id]
    }

    override suspend fun findPublishable(now: Instant, batchSize: Int): List<AiOutbox> {
        publishableCalls.add(now to batchSize)
        callOrder.add(CALL_PUBLISHABLE)

        return outboxes.values
            .filter { it.status == AiOutboxStatus.PENDING && !it.nextRetryAt.isAfter(now) }
            .sortedWith(compareBy({ it.nextRetryAt }, { it.createdAt }))
            .take(batchSize)
    }

    override suspend fun findStalePublishing(threshold: Instant, batchSize: Int): List<AiOutbox> {
        staleCalls.add(threshold to batchSize)
        callOrder.add(CALL_STALE)

        return outboxes.values
            .filter { it.status == AiOutboxStatus.PUBLISHING && it.claim!!.claimedAt.isBefore(threshold) }
            .sortedBy { it.claim!!.claimedAt }
            .take(batchSize)
    }

    override suspend fun findByStatus(status: AiOutboxStatus, batchSize: Int): List<AiOutbox> {
        statusCalls.add(status to batchSize)

        return outboxes.values
            .filter { it.status == status }
            .sortedWith(compareBy({ it.nextRetryAt }, { it.createdAt }))
            .take(batchSize)
    }

    override suspend fun claimPublishing(id: AiOutboxId, claimedBy: String, now: Instant): AiOutbox? {
        claimCalls.add(Triple(id, claimedBy, now))
        callOrder.add(CALL_CLAIM)

        claimResult?.let { return it(id) }

        val stored = outboxes[id] ?: return null
        if (stored.status != AiOutboxStatus.PENDING || stored.nextRetryAt.isAfter(now)) {
            return null
        }

        val claimed = stored.markPublishing(now.truncatedTo(ChronoUnit.MILLIS), claimedBy)
        outboxes[id] = claimed

        return claimed
    }

    /**
     * 저장된 행이 아직 DEAD일 때만 복구 결과를 반영한다. 조건부 쓰기를 그대로 재현한다.
     */
    override suspend fun recoverDead(outbox: AiOutbox): Boolean {
        recoverCalls.add(outbox)

        if (!recoverResult) {
            return false
        }

        val stored = outboxes[outbox.id] ?: return false
        if (stored.status != AiOutboxStatus.DEAD) {
            return false
        }
        outboxes[outbox.id] = outbox

        return true
    }

    override suspend fun finalize(outbox: AiOutbox, expectedClaim: AiOutboxClaim): Boolean {
        finalizeCalls.add(outbox to expectedClaim)
        callOrder.add(CALL_FINALIZE)

        finalizeFailures.removeFirstOrNull()?.let { throw it }

        if (!finalizeResult) {
            return false
        }
        outboxes[outbox.id] = outbox

        return true
    }

    companion object {
        const val CALL_STALE = "stale"
        const val CALL_PUBLISHABLE = "publishable"
        const val CALL_CLAIM = "claim"
        const val CALL_FINALIZE = "finalize"
    }
}
