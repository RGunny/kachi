package me.rgunny.kachi.story.application.port.outbound.outbox

import java.time.Instant
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus

/**
 * outbox 행 저장소 출력 포트.
 */
interface StoryOutboxPersistencePort {

    suspend fun save(outbox: StoryOutbox): StoryOutbox

    suspend fun findById(id: StoryOutboxId): StoryOutbox?

    /**
     * 발행할 차례가 된 PENDING 행을 오래된 순으로 읽는다.
     */
    suspend fun findPublishable(now: Instant, batchSize: Int): List<StoryOutbox>

    /** [threshold]보다 오래 PUBLISHING에 머문 행을 읽는다. */
    suspend fun findStalePublishing(threshold: Instant, batchSize: Int): List<StoryOutbox>

    /**
     * 상태의 행을 오래된 순으로 읽는다.
     */
    suspend fun findByStatus(status: StoryOutboxStatus, batchSize: Int): List<StoryOutbox>

    /**
     * PENDING 행을 PUBLISHING으로 옮기고 소유권을 잡는다. 못 잡으면 null이다.
     */
    suspend fun claimPublishing(id: StoryOutboxId, claimedBy: String, now: Instant): StoryOutbox?

    /**
     * 소유권이 [expectedClaim] 그대로일 때만 발행 결과를 확정한다. [expectedClaim]은 [claimPublishing]이 돌려준 값이다.
     */
    suspend fun finalize(outbox: StoryOutbox, expectedClaim: StoryOutboxClaim): Boolean

    /**
     * DEAD 행일 때만 복구 결과를 저장한다.
     */
    suspend fun recoverDead(outbox: StoryOutbox): Boolean
}
