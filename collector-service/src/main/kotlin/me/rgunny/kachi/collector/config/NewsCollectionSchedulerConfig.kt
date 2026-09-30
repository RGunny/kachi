package me.rgunny.kachi.collector.config

import me.rgunny.kachi.collector.adapter.inbound.scheduler.NewsCollectionSchedulerSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 뉴스 수집 scheduler 설정을 scheduler가 보는 Settings로 조립하는 설정.
 */
@Configuration
class NewsCollectionSchedulerConfig {

    @Bean
    fun newsCollectionSchedulerSettings(
        properties: NewsCollectionSchedulerProperties
    ): NewsCollectionSchedulerSettings {
        return NewsCollectionSchedulerSettings(
            enabled = properties.enabled,
            fixedDelay = properties.fixedDelay,
            initialDelay = properties.initialDelay
        )
    }
}
