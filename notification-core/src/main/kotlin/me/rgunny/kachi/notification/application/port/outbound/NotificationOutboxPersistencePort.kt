package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import java.time.Instant

/**
 * 알림 outbox 영속화 port.
 *
 * 구현체는 PENDING outbox 조회, PUBLISHING claim, 발행 결과 저장을 담당한다.
 * 다중 인스턴스 환경에서 claim은 반드시 저장소 수준의 CAS 조건으로 처리해야 한다.
 */
interface NotificationOutboxPersistencePort {

    suspend fun save(outbox: NotificationOutbox): NotificationOutbox

    suspend fun findById(outboxId: NotificationOutboxId): NotificationOutbox?

    suspend fun findPublishable(now: Instant, batchSize: Int): List<NotificationOutbox>

    suspend fun claimPublishing(
        outboxId: NotificationOutboxId,
        claimedBy: String,
        now: Instant,
    ): NotificationOutbox?

    /**
     * visibility timeout을 넘긴 PUBLISHING outbox 조회.
     *
     * publisher 인스턴스가 claim 후 중단된 경우 recovery scheduler가 실패 처리 또는 재시도 대상으로 삼는다.
     */
    suspend fun findStalePublishing(
        threshold: Instant,
        batchSize: Int,
    ): List<NotificationOutbox>
}
