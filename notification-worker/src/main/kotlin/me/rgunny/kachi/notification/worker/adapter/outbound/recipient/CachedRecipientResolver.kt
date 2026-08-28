package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.outbound.recipient.RecipientResolverPort
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.recipient.RecipientResolveException
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import java.time.Duration

/**
 * 조회 결과를 캐시에 두고, 없을 때만 delegate에 묻는 resolver.
 *
 * 주소가 없다는 결과도 캐시한다. 해지된 바인딩으로 알림이 몰릴 때 원천을 반복 부르지 않기 위해서다.
 * 캐시 읽기·쓰기 실패는 조회 실패로 나간다. 원천으로 우회하면 캐시 장애가 원천 부하로 바뀐다.
 */
class CachedRecipientResolver(
    private val delegate: RecipientResolverPort,
    private val cache: RecipientAddressCache,
    private val ttl: Duration,
) : RecipientResolverPort {

    init {
        require(!ttl.isZero && !ttl.isNegative) { "ttl must be positive" }
    }

    override suspend fun resolve(recipientId: String, channel: NotificationChannel): ResolvedRecipient {
        cached(recipientId, channel)?.let { return it }

        val resolved = delegate.resolve(recipientId, channel)
        store(recipientId, channel, resolved)
        return resolved
    }

    private suspend fun cached(recipientId: String, channel: NotificationChannel): ResolvedRecipient? {
        return try {
            cache.get(recipientId, channel)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw cacheException(recipientId, channel, "get", exception)
        }
    }

    private suspend fun store(recipientId: String, channel: NotificationChannel, resolved: ResolvedRecipient) {
        try {
            cache.put(recipientId, channel, resolved, ttl)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw cacheException(recipientId, channel, "put", exception)
        }
    }

    private fun cacheException(
        recipientId: String,
        channel: NotificationChannel,
        operation: String,
        cause: Exception,
    ): RecipientResolveException {
        return RecipientResolveException(
            recipientId = recipientId,
            channel = channel,
            failure = RetryFailure.of(
                code = RetryFailureCode.RECIPIENT_RESOLVE_FAILED,
                message = "recipient cache $operation failed. recipientId=$recipientId, channel=$channel, cause=${cause.javaClass.simpleName}",
            ),
            cause = cause,
        )
    }
}
