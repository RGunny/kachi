package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationDeduplicationPort
import java.time.Duration

/**
 * worker dispatch 중복 처리 경계를 테스트 안에서 통제하기 위한 deduplication port fake.
 */
class FakeNotificationDeduplicationPort : NotificationDeduplicationPort {
    val releasedKeys = mutableListOf<String>()

    override suspend fun acquire(key: String, ttl: Duration): Boolean {
        return true
    }

    override suspend fun release(key: String) {
        releasedKeys += key
    }
}
