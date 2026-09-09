package me.rgunny.kachi.story.fake

import java.time.Instant
import java.time.temporal.ChronoUnit
import me.rgunny.kachi.story.application.port.outbound.persistence.StoryOutboxPersistencePort
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus

/**
 * outbox 저장소를 메모리로 대신하면서 조회 인자와 호출 순서를 남기는 fake.
 */
class FakeStoryOutboxPersistencePort : StoryOutboxPersistencePort {
    val outboxes: MutableMap<StoryOutboxId, StoryOutbox> = linkedMapOf()

    val publishableCalls: MutableList<Pair<Instant, Int>> = mutableListOf()
    val staleCalls: MutableList<Pair<Instant, Int>> = mutableListOf()
    val statusCalls: MutableList<Pair<StoryOutboxStatus, Int>> = mutableListOf()
    val claimCalls: MutableList<Triple<StoryOutboxId, String, Instant>> = mutableListOf()
    val recoverCalls: MutableList<StoryOutbox> = mutableListOf()
    val finalizeCalls: MutableList<Pair<StoryOutbox, StoryOutboxClaim>> = mutableListOf()
    val callOrder: MutableList<String> = mutableListOf()

    /** null이면 저장된 행을 발행 중으로 옮겨 돌려준다. */
    var claimResult: ((StoryOutboxId) -> StoryOutbox?)? = null
    var finalizeResult: Boolean = true

    /** false로 두면 저장된 상태와 무관하게 복구가 밀린다. */
    var recoverResult: Boolean = true

    /** 호출 순서별 확정 실패. null 자리는 그 호출이 성공한다는 뜻이고, 비면 모두 성공한다. */
    val finalizeFailures: ArrayDeque<Throwable?> = ArrayDeque()

    fun store(vararg outboxes: StoryOutbox) {
        outboxes.forEach { this.outboxes[it.id] = it }
    }

    override suspend fun save(outbox: StoryOutbox): StoryOutbox {
        outboxes[outbox.id] = outbox

        return outbox
    }

    override suspend fun findById(id: StoryOutboxId): StoryOutbox? {
        return outboxes[id]
    }

    override suspend fun findPublishable(now: Instant, batchSize: Int): List<StoryOutbox> {
        publishableCalls.add(now to batchSize)
        callOrder.add(CALL_PUBLISHABLE)

        return outboxes.values
            .filter { it.status == StoryOutboxStatus.PENDING && !it.nextRetryAt.isAfter(now) }
            .sortedWith(compareBy({ it.nextRetryAt }, { it.createdAt }))
            .take(batchSize)
    }

    override suspend fun findStalePublishing(threshold: Instant, batchSize: Int): List<StoryOutbox> {
        staleCalls.add(threshold to batchSize)
        callOrder.add(CALL_STALE)

        return outboxes.values
            .filter { it.status == StoryOutboxStatus.PUBLISHING && it.claim!!.claimedAt.isBefore(threshold) }
            .sortedBy { it.claim!!.claimedAt }
            .take(batchSize)
    }

    override suspend fun findByStatus(status: StoryOutboxStatus, batchSize: Int): List<StoryOutbox> {
        statusCalls.add(status to batchSize)

        return outboxes.values
            .filter { it.status == status }
            .sortedWith(compareBy({ it.nextRetryAt }, { it.createdAt }))
            .take(batchSize)
    }

    override suspend fun claimPublishing(id: StoryOutboxId, claimedBy: String, now: Instant): StoryOutbox? {
        claimCalls.add(Triple(id, claimedBy, now))
        callOrder.add(CALL_CLAIM)

        claimResult?.let { return it(id) }

        val stored = outboxes[id] ?: return null
        if (stored.status != StoryOutboxStatus.PENDING || stored.nextRetryAt.isAfter(now)) {
            return null
        }

        val claimed = stored.markPublishing(now.truncatedTo(ChronoUnit.MILLIS), claimedBy)
        outboxes[id] = claimed

        return claimed
    }

    /** 저장된 행이 아직 DEAD일 때만 복구 결과를 반영한다. */
    override suspend fun recoverDead(outbox: StoryOutbox): Boolean {
        recoverCalls.add(outbox)

        if (!recoverResult) {
            return false
        }

        val stored = outboxes[outbox.id] ?: return false
        if (stored.status != StoryOutboxStatus.DEAD) {
            return false
        }
        outboxes[outbox.id] = outbox

        return true
    }

    override suspend fun finalize(outbox: StoryOutbox, expectedClaim: StoryOutboxClaim): Boolean {
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
