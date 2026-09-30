package me.rgunny.kachi.story.config

import io.netty.channel.ChannelOption
import java.time.Duration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

/** 추론 서버 호출용 WebClient 팩토리. */
object TeiWebClients {

    /** 응답 body를 메모리에 받는 상한. */
    const val MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024

    fun forServer(server: TeiServerProperties): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, server.connectTimeout.toMillis().toInt())
            .responseTimeout(server.timeout)

        return WebClient.builder()
            .baseUrl(server.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE) }
            .build()
    }
}
