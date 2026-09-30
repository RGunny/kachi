package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.application.service.news.KeywordQuarantinePolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 키워드 격리 설정을 정책 객체로 조립하는 설정.
 */
@Configuration
class KeywordQuarantineConfig {

    @Bean
    fun keywordQuarantinePolicy(properties: KeywordQuarantineProperties): KeywordQuarantinePolicy {
        return KeywordQuarantinePolicy(failureThreshold = properties.failureThreshold)
    }
}
