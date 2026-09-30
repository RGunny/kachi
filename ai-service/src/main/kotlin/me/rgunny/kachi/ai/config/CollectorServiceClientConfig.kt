package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.adapter.outbound.news.CollectorServiceNewsReaderAdapter
import me.rgunny.kachi.ai.application.port.outbound.news.NewsReaderPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class CollectorServiceClientConfig {

    /**
     * collector-service 내부 API 호출 전용 WebClient를 구성한다.
     */
    @Bean
    fun collectorServiceWebClient(
        properties: CollectorServiceNewsProperties
    ): WebClient {
        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .codecs { it.defaultCodecs().maxInMemorySize(properties.maxInMemorySize) }
            .build()
    }

    @Bean
    fun newsReaderPort(
        @Qualifier("collectorServiceWebClient") collectorServiceWebClient: WebClient,
        properties: CollectorServiceNewsProperties
    ): NewsReaderPort {
        return CollectorServiceNewsReaderAdapter(
            webClient = collectorServiceWebClient,
            newsPath = properties.newsPath,
            timeout = properties.timeout
        )
    }
}
