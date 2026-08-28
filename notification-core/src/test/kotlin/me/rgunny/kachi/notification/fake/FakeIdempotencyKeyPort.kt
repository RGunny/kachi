package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.domain.NotificationId
import java.time.Duration

class FakeIdempotencyKeyPort(
    private val key: String = "vendor-key",
) : NotificationIdempotencyKeyPort {
    var callCount = 0

    override suspend fun getOrCreate(notificationId: NotificationId, ttl: Duration): String {
        callCount += 1
        return key
    }
}
