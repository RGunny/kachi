package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.outbound.recipient.RecipientResolverPort
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.RecipientUnavailableReason
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.recipient.RecipientResolveException
import me.rgunny.kachi.notification.domain.retry.RetryFailure
import me.rgunny.kachi.notification.domain.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.config.NotificationRecipientProperties
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeoutException

/**
 * user-service 채널 바인딩 조회 internal API로 `(recipientId, channel)`을 수신 주소로 바꾸는 resolver.
 *
 * 재시도와 서킷 브레이커는 두지 않는다. 조회 실패는 RecipientResolveException으로 나가고 재시도는 dispatch의 RETRY_WAIT가 구동한다.
 * 예외 메시지에 응답 본문을 넣지 않는다. 본문에 수신 주소가 있다.
 */
class UserServiceRecipientResolver(
    private val webClient: WebClient,
    private val properties: NotificationRecipientProperties.UserService,
) : RecipientResolverPort {

    override suspend fun resolve(recipientId: String, channel: NotificationChannel): ResolvedRecipient {
        // user-service는 사용자 id를 UUID로만 받는다. 형식이 다르면 400이 되므로 호출하지 않고 "없음"으로 본다.
        val userId = recipientId.toUuidOrNull()
            ?: return UnavailableRecipient(RecipientUnavailableReason.NOT_FOUND)

        val response = fetch(userId, channel)
            ?: return UnavailableRecipient(RecipientUnavailableReason.NOT_FOUND)

        return toResolvedRecipient(response, recipientId, channel)
    }

    /**
     * 200이면 본문을, 404면 null을 돌려준다. 그 밖의 응답은 예외다.
     */
    private suspend fun fetch(userId: UUID, channel: NotificationChannel): UserServiceChannelBindingResponse? {
        val detail = "recipientId=$userId, channel=$channel"

        val lookup = try {
            webClient.get()
                .uri(properties.channelBindingPath, mapOf("userId" to userId.toString(), "channel" to channel.name))
                .exchangeToMono { clientResponse -> readEnvelope(clientResponse, userId, channel) }
                .timeout(properties.timeout)
                .awaitSingle()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: RecipientResolveException) {
            throw exception
        } catch (exception: TimeoutException) {
            throw resolveException(
                code = RetryFailureCode.RECIPIENT_RESOLVE_TIMEOUT,
                detail = "$detail, timeout=${properties.timeout}",
                userId = userId,
                channel = channel,
                cause = exception,
            )
        } catch (exception: Exception) {
            throw resolveException(
                code = RetryFailureCode.RECIPIENT_RESOLVE_FAILED,
                detail = "$detail, cause=${exception.javaClass.simpleName}",
                userId = userId,
                channel = channel,
                cause = exception,
            )
        }

        if (lookup.notFound) {
            return null
        }
        val body = lookup.body
            ?: throw resolveException(RetryFailureCode.RECIPIENT_RESOLVE_FAILED, "$detail, response body missing", userId, channel)
        if (!body.success) {
            throw resolveException(RetryFailureCode.RECIPIENT_RESOLVE_FAILED, "$detail, response success=false", userId, channel)
        }
        return body.data
            ?: throw resolveException(RetryFailureCode.RECIPIENT_RESOLVE_FAILED, "$detail, response data missing", userId, channel)
    }

    private fun readEnvelope(
        clientResponse: ClientResponse,
        userId: UUID,
        channel: NotificationChannel,
    ): Mono<UserServiceChannelBindingLookup> {
        val status = clientResponse.statusCode()
        return when {
            status.value() == HttpStatus.NOT_FOUND.value() ->
                clientResponse.releaseBody().thenReturn(UserServiceChannelBindingLookup.NOT_FOUND)
            status.is2xxSuccessful ->
                clientResponse.bodyToMono(RESPONSE_TYPE).map { UserServiceChannelBindingLookup.found(it) }
            else ->
                clientResponse.releaseBody().then(
                    Mono.error(
                        RecipientResolveException(
                            recipientId = userId.toString(),
                            channel = channel,
                            failure = RetryFailure.of(
                                code = RetryFailureCode.RECIPIENT_RESOLVE_FAILED,
                                message = "recipient resolve failed. recipientId=$userId, channel=$channel, status=${status.value()}",
                                statusCode = status.value(),
                            ),
                        )
                    )
                )
        }
    }

    private fun toResolvedRecipient(
        response: UserServiceChannelBindingResponse,
        recipientId: String,
        channel: NotificationChannel,
    ): ResolvedRecipient {
        if (response.channel != channel.name) {
            return UnavailableRecipient(RecipientUnavailableReason.CHANNEL_MISMATCH)
        }
        return when (response.status) {
            STATUS_ACTIVE -> {
                val address = response.address
                if (address.isNullOrBlank()) {
                    UnavailableRecipient(RecipientUnavailableReason.ADDRESS_MISSING)
                } else {
                    AvailableRecipient(address)
                }
            }
            STATUS_PENDING -> UnavailableRecipient(RecipientUnavailableReason.PENDING)
            STATUS_REVOKED -> UnavailableRecipient(RecipientUnavailableReason.REVOKED)
            else -> throw RecipientResolveException(
                recipientId = recipientId,
                channel = channel,
                failure = RetryFailure.of(
                    code = RetryFailureCode.RECIPIENT_RESOLVE_FAILED,
                    message = "recipient resolve failed. recipientId=$recipientId, channel=$channel, unknown status=${response.status}",
                ),
            )
        }
    }

    private fun resolveException(
        code: RetryFailureCode,
        detail: String,
        userId: UUID,
        channel: NotificationChannel,
        cause: Throwable? = null,
    ): RecipientResolveException {
        return RecipientResolveException(
            recipientId = userId.toString(),
            channel = channel,
            failure = RetryFailure.of(code = code, message = "${code.defaultMessage}. $detail"),
            cause = cause,
        )
    }

    private fun String.toUuidOrNull(): UUID? {
        return runCatching { UUID.fromString(this) }.getOrNull()
    }

    private companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_REVOKED = "REVOKED"
        val RESPONSE_TYPE =
            object : ParameterizedTypeReference<UserServiceApiResponse<UserServiceChannelBindingResponse>>() {}
    }
}
