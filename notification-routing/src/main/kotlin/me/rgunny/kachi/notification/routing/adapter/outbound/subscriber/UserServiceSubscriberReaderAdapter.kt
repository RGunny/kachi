package me.rgunny.kachi.notification.routing.adapter.outbound.subscriber

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.routing.application.port.outbound.subscriber.SubscriberReaderPort
import me.rgunny.kachi.notification.routing.application.port.outbound.subscriber.model.Subscriber
import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.routing.exception.routing.RoutingErrorCode
import me.rgunny.kachi.notification.routing.exception.routing.SubscriberReaderException
import me.rgunny.kachi.notification.routing.config.NotificationRoutingProperties
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient

/**
 * user-service 구독 조회 internal API 클라이언트.
 *
 * 재시도와 서킷 브레이커은 두지 않는다. 실패는 SubscriberReaderException으로 나가고 재시도는 호출자(Kafka consumer)가 한다.
 */
class UserServiceSubscriberReaderAdapter(
    private val webClient: WebClient,
    private val properties: NotificationRoutingProperties.UserService,
) : SubscriberReaderPort {

    override suspend fun findSubscribers(keyword: String): List<Subscriber> {
        val response = try {
            webClient.get()
                .uri { builder ->
                    builder.path(properties.subscriptionsPath)
                        .queryParam("keyword", keyword)
                        .build()
                }
                .retrieve()
                .onStatus({ it.isError }) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .map { body ->
                            SubscriberReaderException(
                                errorCode = RoutingErrorCode.USER_SERVICE_REQUEST_FAILED,
                                detail = "status=${response.statusCode().value()}, body=${body.take(MAX_ERROR_BODY_LENGTH)}",
                            )
                        }
                }
                .bodyToMono(SUBSCRIBERS_RESPONSE_TYPE)
                .timeout(properties.timeout)
                .awaitSingle()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SubscriberReaderException) {
            throw exception
        } catch (exception: Exception) {
            throw SubscriberReaderException(
                errorCode = RoutingErrorCode.USER_SERVICE_REQUEST_FAILED,
                detail = "keyword=$keyword, cause=${exception.javaClass.simpleName}",
                cause = exception,
            )
        }

        if (!response.success) {
            throw SubscriberReaderException(RoutingErrorCode.USER_SERVICE_RESPONSE_FAILED, "keyword=$keyword")
        }
        val subscribers = response.data
            ?: throw SubscriberReaderException(RoutingErrorCode.USER_SERVICE_RESPONSE_MISSING_DATA, "keyword=$keyword")

        return subscribers.map { toSubscriber(it) }
    }

    private fun toSubscriber(response: UserServiceSubscriberResponse): Subscriber {
        return Subscriber(
            userId = response.userId,
            channel = toChannel(response.channel),
            recipientRef = response.recipientRef,
        )
    }

    private fun toChannel(channel: String): NotificationChannel {
        return when (channel) {
            "SLACK" -> NotificationChannel.SLACK
            "DISCORD" -> NotificationChannel.DISCORD
            "TELEGRAM" -> NotificationChannel.TELEGRAM
            else -> throw SubscriberReaderException(
                errorCode = RoutingErrorCode.USER_SERVICE_RESPONSE_INVALID,
                detail = "channel=$channel",
            )
        }
    }

    private companion object {
        const val MAX_ERROR_BODY_LENGTH = 500

        val SUBSCRIBERS_RESPONSE_TYPE =
            object : ParameterizedTypeReference<UserServiceApiResponse<List<UserServiceSubscriberResponse>>>() {}
    }
}
