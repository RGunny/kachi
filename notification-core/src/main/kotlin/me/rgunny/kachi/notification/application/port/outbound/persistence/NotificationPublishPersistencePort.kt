package me.rgunny.kachi.notification.application.port.outbound.persistence

import me.rgunny.kachi.notification.domain.NotificationOutbox
import java.time.Instant

/**
 * notification.dispatch 발행 결과 저장 port.
 *
 * Kafka publish는 DB transaction으로 rollback할 수 없으므로 이 port 밖에서 수행한다.
 * 구현체는 publish 이후의 Outbox 상태와 Notification 상태를 하나의 DB transaction으로 확정해야 한다.
 *
 * 확정 조건은 발행을 시작할 때 잡은 claim이다. 발행하는 사이에 visibility timeout이 지나 다른 tick이 그 행을
 * 회수했다면 claim이 달라져 있고, 그때 도착한 결과는 저장하지 않는다.
 * 늦게 도착한 결과가 회수 이후의 상태를 덮어쓰지 못하게 하는 것이 이 조건의 목적이다.
 */
interface NotificationPublishPersistencePort {

    /**
     * 발행 성공 결과를 확정한다.
     *
     * @param outbox 전이가 끝난 outbox
     * @param expectedClaimedAt 전이 전 outbox가 들고 있던 claim 시각
     * @param expectedClaimedBy 전이 전 outbox가 들고 있던 claim 소유자
     * @return claim이 그대로여서 저장했으면 true, 조건이 어긋나 버렸으면 false
     */
    suspend fun savePublished(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
        now: Instant,
    ): Boolean

    /**
     * 발행 실패 결과를 확정한다. 조건과 반환값의 의미는 [savePublished]와 같다.
     */
    suspend fun savePublishFailed(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
        now: Instant,
        reason: String,
    ): Boolean
}
