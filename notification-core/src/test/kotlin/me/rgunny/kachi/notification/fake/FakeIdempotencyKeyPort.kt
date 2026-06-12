package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.domain.NotificationId
import java.time.Duration

class FakeIdempotencyKeyPort(
    private val key: String = "vendor-key",
) : NotificationIdempotencyKeyPort {

    override suspend fun getOrCreate(notificationId: NotificationId, ttl: Duration): String {
        return key
    }
}
