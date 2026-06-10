package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationId
import java.time.Instant

/**
 * 알림 aggregate 영속화 port.
 *
 * 구현체는 Notification의 현재 상태와 append-only history를 저장한다.
 * requestId는 외부 요청 멱등 키이므로 저장소 수준의 unique 제약으로도 보호해야 한다.
 */
interface NotificationPersistencePort {

    suspend fun save(notification: Notification): Notification

    suspend fun findById(notificationId: NotificationId): Notification?

    suspend fun findByRequestId(requestId: String): Notification?

    /**
     * worker 정상 진입 claim.
     *
     * 저장소 구현체는 현재 상태가 PUBLISHED일 때만 PROCESSING으로 전이되도록
     * CAS 조건을 적용해야 한다.
     */
    suspend fun claimFromPublished(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification?

    /**
     * worker 재시도 진입 claim.
     *
     * 저장소 구현체는 현재 상태가 RETRY_WAIT일 때만 PROCESSING으로 전이되도록
     * CAS 조건을 적용해야 한다.
     */
    suspend fun claimFromRetryWait(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification?
}
