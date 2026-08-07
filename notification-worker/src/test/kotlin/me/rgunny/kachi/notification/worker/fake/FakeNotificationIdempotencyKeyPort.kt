package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.domain.NotificationId
import java.time.Duration

/**
 * vendor 호출용 idempotency key를 고정값으로 제공하는 idempotency port fake.
 */
class FakeNotificationIdempotencyKeyPort : NotificationIdempotencyKeyPort {

    override suspend fun getOrCreate(notificationId: NotificationId, ttl: Duration): String {
        return "vendor-idempotency-key"
    }
}
