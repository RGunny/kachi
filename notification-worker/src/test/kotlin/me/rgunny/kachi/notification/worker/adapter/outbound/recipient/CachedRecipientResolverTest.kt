package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.RecipientUnavailableReason
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.recipient.RecipientResolveException
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.fake.FakeRecipientResolverPort
import me.rgunny.kachi.notification.worker.fake.InMemoryRecipientAddressCache
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("CachedRecipientResolver")
class CachedRecipientResolverTest {

    private val ttl = Duration.ofMinutes(5)

    @Test
    @DisplayName("캐시에 없으면 delegate에 묻고 결과를 TTL과 함께 넣는다")
    fun missThenPut() = runBlocking {
        val delegate = FakeRecipientResolverPort(result = AvailableRecipient(ADDRESS))
        val cache = InMemoryRecipientAddressCache()
        val resolver = CachedRecipientResolver(delegate, cache, ttl)

        val resolved = resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK)

        assertEquals(AvailableRecipient(ADDRESS), resolved)
        assertEquals(1, delegate.calls.size)
        assertEquals(AvailableRecipient(ADDRESS), cache.entries[RECIPIENT_ID to NotificationChannel.SLACK])
        assertEquals(ttl, cache.ttls[RECIPIENT_ID to NotificationChannel.SLACK])
    }

    @Test
    @DisplayName("캐시에 Available이 있으면 delegate를 부르지 않는다")
    fun hitAvailable() = runBlocking {
        val delegate = FakeRecipientResolverPort()
        val cache = InMemoryRecipientAddressCache().also {
            it.entries[RECIPIENT_ID to NotificationChannel.SLACK] = AvailableRecipient(ADDRESS)
        }
        val resolver = CachedRecipientResolver(delegate, cache, ttl)

        val resolved = resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK)

        assertEquals(AvailableRecipient(ADDRESS), resolved)
        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    @DisplayName("캐시에 Unavailable이 있어도 delegate를 부르지 않고 그대로 돌려준다")
    fun hitUnavailable() = runBlocking {
        val delegate = FakeRecipientResolverPort()
        val cache = InMemoryRecipientAddressCache().also {
            it.entries[RECIPIENT_ID to NotificationChannel.SLACK] = UnavailableRecipient(RecipientUnavailableReason.REVOKED)
        }
        val resolver = CachedRecipientResolver(delegate, cache, ttl)

        val resolved = resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK)

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.REVOKED), resolved)
        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    @DisplayName("Unavailable 결과도 캐시에 넣는다")
    fun putUnavailable() = runBlocking {
        val delegate = FakeRecipientResolverPort(result = UnavailableRecipient(RecipientUnavailableReason.PENDING))
        val cache = InMemoryRecipientAddressCache()
        val resolver = CachedRecipientResolver(delegate, cache, ttl)

        resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK)

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.PENDING), cache.entries[RECIPIENT_ID to NotificationChannel.SLACK])
    }

    @Test
    @DisplayName("delegate가 예외를 던지면 캐시하지 않고 그대로 전파한다")
    fun delegateFailureIsNotCached() {
        val failure = RecipientResolveException(
            recipientId = RECIPIENT_ID,
            channel = NotificationChannel.SLACK,
            failure = RetryFailure.of(RetryFailureCode.RECIPIENT_RESOLVE_FAILED),
        )
        val cache = InMemoryRecipientAddressCache()
        val resolver = CachedRecipientResolver(FakeRecipientResolverPort(failure = failure), cache, ttl)

        val thrown = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(failure, thrown)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    @DisplayName("캐시 읽기가 실패하면 delegate를 부르지 않고 RECIPIENT_RESOLVE_FAILED 예외다")
    fun cacheGetFailure() {
        val delegate = FakeRecipientResolverPort()
        val cache = InMemoryRecipientAddressCache(getFailure = IllegalStateException("redis-down"))
        val resolver = CachedRecipientResolver(delegate, cache, ttl)

        val thrown = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, thrown.failure.code)
        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    @DisplayName("캐시 쓰기가 실패하면 RECIPIENT_RESOLVE_FAILED 예외다")
    fun cachePutFailure() {
        val cache = InMemoryRecipientAddressCache(putFailure = IllegalStateException("redis-down"))
        val resolver = CachedRecipientResolver(FakeRecipientResolverPort(), cache, ttl)

        val thrown = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, thrown.failure.code)
    }

    @Test
    @DisplayName("TTL이 양수가 아니면 만들 수 없다")
    fun rejectNonPositiveTtl() {
        assertFailsWith<IllegalArgumentException> {
            CachedRecipientResolver(FakeRecipientResolverPort(), InMemoryRecipientAddressCache(), Duration.ZERO)
        }
    }

    private companion object {
        const val RECIPIENT_ID = "0d4a8b0e-4f0a-4a3e-9a5f-1c2b3d4e5f60"
        const val ADDRESS = "https://hooks.slack.test/services/T000/B000/XXXX"
    }
}
