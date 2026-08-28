package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration

/**
 * 수신 주소 조회 결과의 Redis 캐시. 키는 `notification:recipient:{recipientId}:{CHANNEL}`이다.
 *
 * ADDRESS-EXPOSURE: 수신 주소가 TTL 동안 Redis에 남는다. 경계는 ADR 028에 있다.
 */
class RedisRecipientAddressCache(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val codec: ResolvedRecipientCacheCodec,
) : RecipientAddressCache {

    override suspend fun get(recipientId: String, channel: NotificationChannel): ResolvedRecipient? {
        val value = redisTemplate.opsForValue().get(cacheKey(recipientId, channel)).awaitSingleOrNull()
            ?: return null
        return codec.decode(value)
    }

    override suspend fun put(
        recipientId: String,
        channel: NotificationChannel,
        resolved: ResolvedRecipient,
        ttl: Duration,
    ) {
        require(!ttl.isZero && !ttl.isNegative) { "ttl must be positive" }
        redisTemplate.opsForValue()
            .set(cacheKey(recipientId, channel), codec.encode(resolved), ttl)
            .awaitSingle()
    }

    companion object {
        fun cacheKey(recipientId: String, channel: NotificationChannel): String {
            return "notification:recipient:$recipientId:${channel.name}"
        }
    }
}
