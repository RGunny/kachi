package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.RecipientUnavailableReason
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("test")
@DataRedisTest
@Import(RedisTestContainersConfig::class)
@DisplayName("RedisRecipientAddressCache 통합")
class RedisRecipientAddressCacheIntegrationTest {

    @Autowired
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    @DisplayName("넣은 Available을 같은 키로 읽는다")
    fun putThenGetAvailable() = runBlocking {
        val cache = cache()
        val recipientId = UUID.randomUUID().toString()

        cache.put(recipientId, NotificationChannel.SLACK, AvailableRecipient(ADDRESS), Duration.ofMinutes(5))

        assertEquals(AvailableRecipient(ADDRESS), cache.get(recipientId, NotificationChannel.SLACK))
        assertEquals(
            true,
            redisTemplate.hasKey("notification:recipient:$recipientId:SLACK").awaitSingle(),
        )
    }

    @Test
    @DisplayName("넣은 Unavailable을 같은 키로 읽는다")
    fun putThenGetUnavailable() = runBlocking {
        val cache = cache()
        val recipientId = UUID.randomUUID().toString()

        cache.put(recipientId, NotificationChannel.TELEGRAM, UnavailableRecipient(RecipientUnavailableReason.REVOKED), Duration.ofMinutes(5))

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.REVOKED), cache.get(recipientId, NotificationChannel.TELEGRAM))
    }

    @Test
    @DisplayName("TTL이 설정값 이하의 양수로 걸린다")
    fun ttl() = runBlocking {
        val cache = cache()
        val recipientId = UUID.randomUUID().toString()

        cache.put(recipientId, NotificationChannel.SLACK, AvailableRecipient(ADDRESS), Duration.ofMinutes(5))

        val remaining = redisTemplate.getExpire("notification:recipient:$recipientId:SLACK").awaitSingle()
        assertTrue(remaining > Duration.ZERO && remaining <= Duration.ofMinutes(5), "ttl=$remaining")
    }

    @Test
    @DisplayName("없는 키는 null이다")
    fun missing() = runBlocking {
        val cache = cache()

        assertNull(cache.get(UUID.randomUUID().toString(), NotificationChannel.SLACK))
    }

    private fun cache(): RedisRecipientAddressCache {
        return RedisRecipientAddressCache(
            redisTemplate = redisTemplate,
            codec = ResolvedRecipientCacheCodec(JsonMapper.builder().build()),
        )
    }

    private companion object {
        const val ADDRESS = "https://hooks.slack.test/services/T000/B000/XXXX"
    }
}
