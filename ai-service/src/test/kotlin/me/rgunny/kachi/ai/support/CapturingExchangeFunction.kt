package me.rgunny.kachi.ai.support

import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 고정 응답을 돌려주면서 보낸 요청을 보관하는 ExchangeFunction.
 *
 * adapter가 어떤 경로/헤더/본문으로 호출했는지까지 확인해야 할 때 쓴다.
 * 요청 본문은 inserter라 바로 읽을 수 없으므로 mock 요청에 써 넣은 뒤 문자열로 꺼낸다.
 */
class CapturingExchangeFunction(
    private val responseBody: String,
    private val status: HttpStatus = HttpStatus.OK
) : ExchangeFunction {

    lateinit var request: ClientRequest
    private var sentBodyText: String = ""

    /** 보낸 요청 본문을 JSON으로 읽은 것. 본문이 없었으면 빈 트리다. */
    val sentBody: JsonNode
        get() = JSON.readTree(sentBodyText.ifBlank { "{}" })

    override fun exchange(request: ClientRequest): Mono<ClientResponse> {
        this.request = request
        val sink = MockClientHttpRequest(request.method(), request.url())

        return request.writeTo(sink, ExchangeStrategies.withDefaults())
            .then(Mono.defer { sink.bodyAsString })
            .doOnNext { sentBodyText = it }
            .then(Mono.fromSupplier { response() })
    }

    private fun response(): ClientResponse {
        return ClientResponse.create(status)
            .header("Content-Type", "application/json")
            .body(responseBody)
            .build()
    }

    private companion object {
        val JSON: JsonMapper = JsonMapper.builder().build()
    }
}
