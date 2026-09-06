package me.rgunny.kachi.ai.config

import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * 모델 하나의 WebClient에 붙는 응답 timeout.
 *
 * 같은 제공자의 모델들이 주소와 연결 설정은 공유하되 응답을 기다리는 시간은 모델마다 다르므로, 연결 설정이 아니라 필터로 건다.
 * 요청 시작부터 응답 본문까지가 [timeout] 안에 끝나지 않으면 [java.util.concurrent.TimeoutException]으로 끝내고 요청을 취소한다.
 */
class LlmResponseTimeout(
    private val timeout: Duration
) : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
        return next.exchange(request).timeout(timeout)
    }
}
