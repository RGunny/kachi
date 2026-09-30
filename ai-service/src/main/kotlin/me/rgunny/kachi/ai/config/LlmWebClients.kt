package me.rgunny.kachi.ai.config

import io.netty.channel.ChannelOption
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

/**
 * LLM 호출용 WebClient를 두 층으로 만드는 팩토리.
 *
 * 제공자 WebClient에는 제공자의 값(주소·연결 timeout·인증 헤더·응답 크기 상한)이 있고,
 * 모델 WebClient는 그것을 복제해 모델의 값(응답 timeout)만 덧붙인 것이다.
 * 같은 제공자의 모델들은 연결 설정을 한 번만 적고 응답 timeout만 따로 갖는다.
 * 어느 층의 값인지가 이 두 메서드로 갈리므로, 제공자 단위 설정이 늘면 [forProvider]만, 모델 단위 설정이 늘면 [forModel]만 바뀐다.
 */
object LlmWebClients {

    /** 응답 body를 메모리에 받는 상한. 요약 응답은 수 KB. */
    const val MAX_IN_MEMORY_SIZE = 512 * 1024

    fun forProvider(
        baseUrl: String,
        apiKey: String,
        connectTimeout: Duration
    ): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())

        val builder = WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE) }

        // 인증이 없는 제공자(self-hosted)에는 헤더를 싣지 않는다.
        if (apiKey.isNotBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        }

        return builder.build()
    }

    fun forModel(
        providerWebClient: WebClient,
        responseTimeout: Duration
    ): WebClient {
        return providerWebClient.mutate()
            .filter(LlmResponseTimeout(responseTimeout))
            .build()
    }
}
