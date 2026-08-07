package me.rgunny.kachi.ai.support

import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

/**
 * 고정 응답을 돌려주면서 보낸 요청을 보관하는 ExchangeFunction.
 *
 * adapter가 어떤 경로/헤더로 호출했는지까지 확인해야 할 때 쓴다.
 */
class CapturingExchangeFunction(
    private val body: String,
    private val status: HttpStatus = HttpStatus.OK
) : ExchangeFunction {

    lateinit var request: ClientRequest

    override fun exchange(request: ClientRequest): Mono<ClientResponse> {
        this.request = request

        return Mono.just(
            ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(body)
                .build()
        )
    }
}
