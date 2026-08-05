package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto.TelegramApiResponse
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto.TelegramResponseParameters
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto.TelegramSendMessageRequest
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto.TelegramSendMessageResult
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

internal class TelegramSendMessageClient(
    private val webClient: WebClient,
    private val botToken: String,
    private val sendMessagePath: String,
) {

    suspend fun sendMessage(chatId: String, text: String): TelegramSendMessageResult {
        return webClient.post()
            .uri("/bot$botToken$sendMessagePath")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                TelegramSendMessageRequest(
                    chatId = chatId,
                    text = text,
                )
            )
            .exchangeToMono { clientResponse ->
                clientResponse.bodyToMono<TelegramApiResponse>()
                    .defaultIfEmpty(TelegramApiResponse())
                    .map { body ->
                        TelegramSendMessageResult(
                            statusCode = clientResponse.statusCode().value(),
                            ok = body.ok,
                            errorCode = body.errorCode,
                            description = body.description,
                            retryAfterMillis = retryAfterMillis(body.parameters),
                        )
                    }
            }
            .awaitSingle()
    }

    private fun retryAfterMillis(parameters: TelegramResponseParameters?): Long? {
        return parameters?.retryAfter
            ?.takeIf { it >= 0 }
            ?.let { it * 1000 }
    }
}
