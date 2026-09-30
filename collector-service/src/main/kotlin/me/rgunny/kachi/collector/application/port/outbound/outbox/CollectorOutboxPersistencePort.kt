package me.rgunny.kachi.collector.application.port.outbound.outbox

import java.time.Instant
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxClaim
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus

/**
 * outbox 행 저장소 출력 포트.
 *
 * 상태 전이는 도메인이 계산하고 이 포트는 그 결과를 조건부로 저장한다.
 * [claimPublishing]과 [finalize]는 조건이 맞을 때만 쓰기가 일어나는 단일 문서 연산이라
 * 여러 인스턴스가 같은 행을 집어가도 하나만 성공한다.
 */
interface CollectorOutboxPersistencePort {

    suspend fun save(outbox: CollectorOutbox): CollectorOutbox

    suspend fun findById(id: CollectorOutboxId): CollectorOutbox?

    /**
     * 발행할 차례가 된 PENDING 행을 오래된 순으로 읽는다.
     */
    suspend fun findPublishable(now: Instant, batchSize: Int): List<CollectorOutbox>

    /**
     * [threshold]보다 오래 PUBLISHING에 머문 행을 읽는다. 발행 도중 죽은 인스턴스가 남긴 행이다.
     */
    suspend fun findStalePublishing(threshold: Instant, batchSize: Int): List<CollectorOutbox>

    /**
     * 상태의 행을 오래된 순으로 읽는다.
     */
    suspend fun findByStatus(status: CollectorOutboxStatus, batchSize: Int): List<CollectorOutbox>

    /**
     * 발행할 차례가 된 PENDING 행을 PUBLISHING으로 옮기고 소유권을 잡는다.
     * 다른 인스턴스가 먼저 잡았거나 아직 차례가 아니면 null을 반환한다.
     */
    suspend fun claimPublishing(id: CollectorOutboxId, claimedBy: String, now: Instant): CollectorOutbox?

    /**
     * 소유권이 [expectedClaim] 그대로일 때만 발행 결과를 확정한다.
     *
     * 회수된 뒤 돌아온 늦은 결과는 조건에 걸려 저장되지 않는다. false는 그 사실을 알린다.
     *
     * [expectedClaim]은 반드시 [claimPublishing]이 돌려준 값을 그대로 넘긴다.
     * 점유 시각은 저장 정밀도에 맞춰 잘린 값이므로, 호출자가 자기 시계로 같은 값을 다시 만들 수는 없다.
     */
    suspend fun finalize(outbox: CollectorOutbox, expectedClaim: CollectorOutboxClaim): Boolean

    /**
     * DEAD 행일 때만 복구 결과를 저장한다. false는 조회와 저장 사이에 다른 복구가 끝났다는 뜻이다.
     *
     * [outbox]는 `recoverToPending`이 계산한 PENDING 상태다.
     */
    suspend fun recoverDead(outbox: CollectorOutbox): Boolean
}
