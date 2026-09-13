package me.rgunny.kachi.story.config

import me.rgunny.kachi.story.adapter.inbound.scheduler.IndexCleanupSchedulerSettings
import me.rgunny.kachi.story.adapter.inbound.scheduler.StoryCloseSchedulerSettings
import me.rgunny.kachi.story.application.service.close.StoryClosePolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 주기 작업 규칙과 scheduler 설정을 설정 값에서 조립하는 설정.
 */
@Configuration
@EnableConfigurationProperties(StoryJobsProperties::class)
class StoryJobsConfig {

    @Bean
    fun storyClosePolicy(properties: StoryJobsProperties): StoryClosePolicy {
        return properties.close.toPolicy()
    }

    @Bean
    fun storyCloseSchedulerSettings(properties: StoryJobsProperties): StoryCloseSchedulerSettings {
        return StoryCloseSchedulerSettings(
            enabled = properties.close.enabled,
            interval = properties.close.interval,
            initialDelay = properties.close.initialDelay,
            closeAfter = properties.close.closeAfter,
            batchLimit = properties.close.batchLimit
        )
    }

    @Bean
    fun indexCleanupSchedulerSettings(properties: StoryJobsProperties): IndexCleanupSchedulerSettings {
        return IndexCleanupSchedulerSettings(
            enabled = properties.cleanup.enabled,
            interval = properties.cleanup.interval,
            initialDelay = properties.cleanup.initialDelay
        )
    }
}
