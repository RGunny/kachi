package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.NotificationOutbox
import java.time.Instant

/**
 * notification.dispatch 발행 결과 저장 port.
 *
 * Kafka publish는 DB transaction으로 rollback할 수 없으므로 이 port 밖에서 수행한다.
 * 구현체는 publish 이후의 Outbox 상태와 Notification 상태를 하나의 DB transaction으로 확정해야 한다.
 */
interface NotificationPublishPersistencePort {

    suspend fun savePublished(
        outbox: NotificationOutbox,
        now: Instant,
    )

    suspend fun savePublishFailed(
        outbox: NotificationOutbox,
        now: Instant,
        reason: String,
    )
}
