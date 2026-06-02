package me.rgunny.kachi.collector.config

import me.rgunny.kachi.collector.adapter.out.keyword.UserServiceKeywordProperties
import me.rgunny.kachi.collector.adapter.out.keyword.UserServiceKeywordReaderAdapter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(UserServiceKeywordProperties::class)
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

    /**
     * collector-service의 KeywordReaderPort를 user-service 내부 API 기반으로 구현한다.
     */
    @Bean
    fun userServiceKeywordReaderAdapter(
        @Qualifier("userServiceWebClient")
        userServiceWebClient: WebClient,
        properties: UserServiceKeywordProperties
    ): UserServiceKeywordReaderAdapter {
        return UserServiceKeywordReaderAdapter(
            webClient = userServiceWebClient,
            properties = properties
        )
    }
}
