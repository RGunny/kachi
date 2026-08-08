package me.rgunny.kachi.notification.worker.adapter.outbound.idempotency

import com.github.f4b6a3.uuid.UuidCreator
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.domain.NotificationId
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * vendor 호출용 idempotency key Redis adapter.
 *
 * 같은 notificationId의 retry는 같은 key를 재사용해야 외부 vendor가 중복 발송을 차단할 수 있다.
 */
@Component
class RedisNotificationIdempotencyKeyAdapter(
    private val redisTemplate: ReactiveStringRedisTemplate,
) : NotificationIdempotencyKeyPort {

    override suspend fun getOrCreate(
        notificationId: NotificationId,
        ttl: Duration,
    ): String {
        require(!ttl.isZero && !ttl.isNegative) { "ttl must be positive" }

        // 1. 이미 발급된 key가 있으면 그대로 재사용한다.
        val key = idempotencyKey(notificationId)
        val existing = redisTemplate.opsForValue().get(key).awaitSingleOrNull()
        if (existing != null) {
            return existing
        }

        // 2. 처음 보는 notificationId면 새 key 후보를 만들고 Redis SET NX로 선점한다.
        val candidate = UuidCreator.getTimeOrderedEpoch().toString()
        val created = redisTemplate.opsForValue()
            .setIfAbsent(key, candidate, ttl)
            .awaitSingle()

        // 3. 선점에 성공한 worker는 방금 만든 key를 반환한다.
        if (created) {
            return candidate
        }

        // 4. 선점에 실패했다면 다른 worker가 먼저 만든 key를 읽어 재사용한다.
        // Redis replication/visibility 지연 같은 극단적 edge를 고려해 짧게 재조회한다.
        repeat(RACE_READ_RETRY_COUNT) { retryIndex ->
            val racedValue = redisTemplate.opsForValue().get(key).awaitSingleOrNull()
            if (racedValue != null) {
                return racedValue
            }
            log.warn(
                "idempotency key set-if-absent lost race but value is not visible yet. notificationId={} retry={}",
                notificationId.id,
                retryIndex + 1,
            )
        }

        // 5. SET NX race에서 졌는데도 값을 끝내 읽지 못하면 Redis 상태를 신뢰할 수 없으므로 명시 예외로 중단한다.
        throw IdempotencyKeyCreationException(
            "idempotency key creation raced but value not found. notificationId=${notificationId.id}"
        )
    }

    private fun idempotencyKey(notificationId: NotificationId): String {
        return "notification:vendor-idempotency:${notificationId.id}"
    }

    private companion object {
        const val RACE_READ_RETRY_COUNT = 2
        val log = LoggerFactory.getLogger(RedisNotificationIdempotencyKeyAdapter::class.java)
    }
}
