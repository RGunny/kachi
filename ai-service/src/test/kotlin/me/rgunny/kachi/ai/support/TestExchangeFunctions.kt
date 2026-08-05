package me.rgunny.kachi.ai.support

import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

/**
 * 외부 서비스 호출 adapter 테스트에서 실제 네트워크 없이 응답을 고정하는 ExchangeFunction.
 */
fun jsonExchangeFunction(
    responseBody: String,
    status: HttpStatus
): ExchangeFunction {
    return ExchangeFunction {
        Mono.just(
            ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(responseBody)
                .build()
        )
    }
}
