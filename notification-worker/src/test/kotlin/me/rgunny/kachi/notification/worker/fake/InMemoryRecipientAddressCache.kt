package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.RecipientAddressCache
import java.time.Duration

/**
 * 메모리 맵으로 동작하는 수신 주소 캐시. 넣은 TTL을 기록하고, 지정하면 읽기·쓰기에서 예외를 던진다.
 */
class InMemoryRecipientAddressCache(
    var getFailure: RuntimeException? = null,
    var putFailure: RuntimeException? = null,
) : RecipientAddressCache {
    val entries = mutableMapOf<Pair<String, NotificationChannel>, ResolvedRecipient>()
    val ttls = mutableMapOf<Pair<String, NotificationChannel>, Duration>()

    override suspend fun get(recipientId: String, channel: NotificationChannel): ResolvedRecipient? {
        getFailure?.let { throw it }
        return entries[recipientId to channel]
    }

    override suspend fun put(
        recipientId: String,
        channel: NotificationChannel,
        resolved: ResolvedRecipient,
        ttl: Duration,
    ) {
        putFailure?.let { throw it }
        entries[recipientId to channel] = resolved
        ttls[recipientId to channel] = ttl
    }
}
