package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationDeduplicationPort
import java.time.Duration

class FakeDeduplicationPort(
    private val acquireResult: Boolean = true,
) : NotificationDeduplicationPort {
    val releasedKeys = mutableListOf<String>()

    override suspend fun acquire(key: String, ttl: Duration): Boolean {
        return acquireResult
    }

    override suspend fun release(key: String) {
        releasedKeys += key
    }
}
