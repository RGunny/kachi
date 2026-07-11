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
     * PROCESSING 상태로 claim된 뒤 visibility timeout을 넘긴 알림 조회.
     *
     * worker가 vendor 호출 중단, process crash, DB finalize 실패 등으로 종료되면 PROCESSING 상태가 남을 수 있다.
     * recovery use case는 이 목록을 재시도 대기 또는 DEAD 상태로 회수한다.
     */
    suspend fun findStaleProcessing(
        threshold: Instant,
        batchSize: Int,
    ): List<Notification>

    /**
     * PUBLISHED 상태의 알림을 처리 중 상태로 claim한다.
     *
     * 구현체는 현재 상태가 PUBLISHED인 경우에만 PROCESSING으로 전이되도록 CAS 조건을 적용해야 한다.
     * claim에 성공하면 PROCESSING으로 전이된 Notification을 반환하고, 이미 선점됐거나 상태가 변경된 경우 null을 반환한다.
     */
    suspend fun claimFromPublished(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification?

    /**
     * RETRY_WAIT 상태의 알림을 처리 중 상태로 claim한다.
     *
     * 구현체는 현재 상태가 RETRY_WAIT인 경우에만 PROCESSING으로 전이되도록 CAS 조건을 적용해야 한다.
     * claim에 성공하면 PROCESSING으로 전이된 Notification을 반환하고, 이미 선점됐거나 상태가 변경된 경우 null을 반환한다.
     */
    suspend fun claimFromRetryWait(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification?
}
