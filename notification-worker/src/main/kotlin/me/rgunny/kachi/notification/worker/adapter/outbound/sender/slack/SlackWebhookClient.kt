package me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.dto.SlackWebhookRequest
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.dto.SlackWebhookResult
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Slack incoming webhook HTTP 호출. webhook URL은 수신 주소이므로 호출마다 받는다.
 */
internal class SlackWebhookClient(
    private val webClient: WebClient,
) {

    suspend fun send(webhookUrl: String, text: String): SlackWebhookResult {
        return webClient.post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(SlackWebhookRequest(text = text))
            .exchangeToMono { clientResponse ->
                Mono.just(
                    SlackWebhookResult(
                        statusCode = clientResponse.statusCode().value(),
                        retryAfterMillis = retryAfterMillis(clientResponse.headers().asHttpHeaders()),
                    )
                )
            }
            .awaitSingle()
    }

    private fun retryAfterMillis(headers: HttpHeaders): Long? {
        return headers.getFirst(HttpHeaders.RETRY_AFTER)
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?.let { it * 1000 }
    }
}

