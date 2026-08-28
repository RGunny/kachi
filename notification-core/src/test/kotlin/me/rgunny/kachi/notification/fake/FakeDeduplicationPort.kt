package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationDeduplicationPort
import java.time.Duration

/**
 * 키별 acquire 결과를 지정하고 acquire·release 호출을 기록하는 dedupe port fake.
 * 지정하지 않은 키는 [acquireResult]를 따른다.
 */
class FakeDeduplicationPort(
    private val acquireResult: Boolean = true,
    private val acquireResults: Map<String, Boolean> = emptyMap(),
) : NotificationDeduplicationPort {
    val acquiredKeys = mutableListOf<Pair<String, Duration>>()
    val releasedKeys = mutableListOf<String>()

    override suspend fun acquire(key: String, ttl: Duration): Boolean {
        acquiredKeys += key to ttl
        return acquireResults[key] ?: acquireResult
    }

    override suspend fun release(key: String) {
        releasedKeys += key
    }
}
