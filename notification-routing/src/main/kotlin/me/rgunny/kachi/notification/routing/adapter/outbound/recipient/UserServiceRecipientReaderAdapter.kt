package me.rgunny.kachi.notification.routing.adapter.outbound.recipient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.routing.application.port.outbound.recipient.RecipientReaderPort
import me.rgunny.kachi.notification.routing.application.port.outbound.recipient.model.Recipient
import me.rgunny.kachi.notification.routing.config.NotificationRoutingProperties
import me.rgunny.kachi.notification.routing.exception.routing.RecipientReaderException
import me.rgunny.kachi.notification.routing.exception.routing.RoutingErrorCode
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder

/**
 * user-service 수신자 조회 internal API 클라이언트. 구독자는 키워드로, 관리자는 역할로 묻는다.
 *
 * 재시도와 서킷 브레이커는 두지 않는다. 실패는 RecipientReaderException으로 나가고 재시도는 호출자(Kafka consumer)가 한다.
 */
class UserServiceRecipientReaderAdapter(
    private val webClient: WebClient,
    private val properties: NotificationRoutingProperties.UserService,
) : RecipientReaderPort {

    override suspend fun findSubscribers(keyword: String): List<Recipient> {
        val subscribers = fetch(SUBSCRIBERS_RESPONSE_TYPE, "keyword=$keyword") { builder ->
            builder.path(properties.subscriptionsPath).queryParam("keyword", keyword)
        }

        return subscribers.map { Recipient(recipientId = it.userId, channel = toChannel(it.channel)) }
    }

    override suspend fun findAdmins(): List<Recipient> {
        val users = fetch(USER_CHANNELS_RESPONSE_TYPE, "role=$ADMIN_ROLE") { builder ->
            builder.path(properties.usersPath).queryParam("role", ADMIN_ROLE)
        }

        return users.flatMap { user ->
            user.channels.map { channel -> Recipient(recipientId = user.userId, channel = toChannel(channel)) }
        }
    }

    private suspend fun <T> fetch(
        responseType: ParameterizedTypeReference<UserServiceApiResponse<List<T>>>,
        detail: String,
        uri: (UriBuilder) -> UriBuilder,
    ): List<T> {
        val response = try {
            webClient.get()
                .uri { builder -> uri(builder).build() }
                .retrieve()
                .onStatus({ it.isError }) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .map { body ->
                            RecipientReaderException(
                                errorCode = RoutingErrorCode.USER_SERVICE_REQUEST_FAILED,
                                detail = "status=${response.statusCode().value()}, body=${body.take(MAX_ERROR_BODY_LENGTH)}",
                            )
                        }
                }
                .bodyToMono(responseType)
                .timeout(properties.timeout)
                .awaitSingle()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: RecipientReaderException) {
            throw exception
        } catch (exception: Exception) {
            throw RecipientReaderException(
                errorCode = RoutingErrorCode.USER_SERVICE_REQUEST_FAILED,
                detail = "$detail, cause=${exception.javaClass.simpleName}",
                cause = exception,
            )
        }

        if (!response.success) {
            throw RecipientReaderException(RoutingErrorCode.USER_SERVICE_RESPONSE_FAILED, detail)
        }

        return response.data
            ?: throw RecipientReaderException(RoutingErrorCode.USER_SERVICE_RESPONSE_MISSING_DATA, detail)
    }

    private fun toChannel(channel: String): NotificationChannel {
        return when (channel) {
            "SLACK" -> NotificationChannel.SLACK
            "DISCORD" -> NotificationChannel.DISCORD
            "TELEGRAM" -> NotificationChannel.TELEGRAM
            else -> throw RecipientReaderException(
                errorCode = RoutingErrorCode.USER_SERVICE_RESPONSE_INVALID,
                detail = "channel=$channel",
            )
        }
    }

    private companion object {
        const val MAX_ERROR_BODY_LENGTH = 500
        const val ADMIN_ROLE = "ADMIN"

        val SUBSCRIBERS_RESPONSE_TYPE =
            object : ParameterizedTypeReference<UserServiceApiResponse<List<UserServiceSubscriberResponse>>>() {}
        val USER_CHANNELS_RESPONSE_TYPE =
            object : ParameterizedTypeReference<UserServiceApiResponse<List<UserServiceUserChannelsResponse>>>() {}
    }
}
