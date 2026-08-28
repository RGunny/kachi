package me.rgunny.kachi.notification.worker.support

import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * 외부 서비스 호출 adapter 테스트에서 실제 네트워크 없이 응답을 고정하는 ExchangeFunction.
 */
fun jsonExchangeFunction(
    responseBody: String,
    status: HttpStatus = HttpStatus.OK,
    delay: Duration = Duration.ZERO,
): ExchangeFunction {
    return ExchangeFunction {
        Mono.just(
            ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(responseBody)
                .build()
        ).delayElement(delay)
    }
}

/**
 * 고정 응답을 돌려주면서 보낸 요청을 보관하는 ExchangeFunction.
 */
class CapturingJsonExchangeFunction(
    private val body: String,
    private val status: HttpStatus = HttpStatus.OK,
) : ExchangeFunction {

    lateinit var request: ClientRequest
    var exchangeCount = 0

    override fun exchange(request: ClientRequest): Mono<ClientResponse> {
        this.request = request
        exchangeCount += 1
        return Mono.just(
            ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(body)
                .build()
        )
    }
}
