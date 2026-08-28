package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationDeduplicationPort
import java.time.Duration

/**
 * worker dispatch 중복 처리 경계를 테스트 안에서 통제하기 위한 deduplication port fake.
 *
 * 선점된 키를 들고 있어 같은 키의 두 번째 acquire는 false다. TTL은 흉내 내지 않으므로 만료는 [expire]로 만든다.
 */
class FakeNotificationDeduplicationPort : NotificationDeduplicationPort {
    private val held = mutableSetOf<String>()
    val releasedKeys = mutableListOf<String>()

    override suspend fun acquire(key: String, ttl: Duration): Boolean {
        return held.add(key)
    }

    override suspend fun release(key: String) {
        held -= key
        releasedKeys += key
    }

    /** TTL 만료를 흉내 낸다. release 기록에는 남지 않는다. */
    fun expire(key: String) {
        held -= key
    }

    fun isHeld(key: String): Boolean = key in held
}
