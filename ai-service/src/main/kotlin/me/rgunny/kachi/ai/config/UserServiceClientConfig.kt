package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.adapter.outbound.keyword.UserServiceKeywordReaderAdapter
import me.rgunny.kachi.ai.application.port.outbound.keyword.KeywordReaderPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class UserServiceClientConfig {

    /**
     * user-service 내부 API 호출 전용 WebClient를 구성한다.
     */
    @Bean
    fun userServiceWebClient(
        properties: UserServiceKeywordProperties
    ): WebClient {
        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .codecs { it.defaultCodecs().maxInMemorySize(properties.maxInMemorySize) }
            .build()
    }

    @Bean
    fun keywordReaderPort(
        @Qualifier("userServiceWebClient") userServiceWebClient: WebClient,
        properties: UserServiceKeywordProperties
    ): KeywordReaderPort {
        return UserServiceKeywordReaderAdapter(
            webClient = userServiceWebClient,
            activeKeywordsPath = properties.activeKeywordsPath,
            timeout = properties.timeout
        )
    }
}
