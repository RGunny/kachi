package me.rgunny.kachi.story.config

import java.time.Clock
import me.rgunny.kachi.story.adapter.inbound.scheduler.StoryOutboxRelaySchedulerSettings
import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPersistencePort
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPublisherPort
import me.rgunny.kachi.story.application.service.outbox.RelayStoryOutboxService
import me.rgunny.kachi.story.application.service.outbox.StoryOutboxRelayPolicy
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * relay가 켜져 있을 때만 relay 유스케이스와 실행 정책을 조립하는 설정.
 */
@Configuration
@ConditionalOnProperty(
    prefix = StoryOutboxRelayProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true"
)
class StoryOutboxRelayConfig {

    @Bean
    fun storyOutboxRelayPolicy(properties: StoryOutboxRelayProperties): StoryOutboxRelayPolicy {
        return properties.toPolicy()
    }

    @Bean
    fun relayStoryOutboxUseCase(
        outboxPersistencePort: StoryOutboxPersistencePort,
        publisherPort: ObjectProvider<StoryOutboxPublisherPort>,
        policy: StoryOutboxRelayPolicy,
        clock: Clock
    ): RelayStoryOutboxUseCase {
        val publisher = checkNotNull(publisherPort.ifAvailable) { NO_PUBLISH_TARGET_MESSAGE }

        return RelayStoryOutboxService(
            outboxPersistencePort = outboxPersistencePort,
            publisherPort = publisher,
            policy = policy,
            clock = clock
        )
    }

    @Bean
    fun storyOutboxRelaySchedulerSettings(properties: StoryOutboxRelayProperties): StoryOutboxRelaySchedulerSettings {
        return StoryOutboxRelaySchedulerSettings(
            publisherId = properties.publisherId,
            fixedDelay = properties.fixedDelay,
            initialDelay = properties.initialDelay,
            batchSize = properties.batchSize,
            publishingVisibilityTimeout = properties.publishingVisibilityTimeout
        )
    }

    companion object {
        const val NO_PUBLISH_TARGET_MESSAGE =
            "outbox relay가 켜져 있으나 발행 어댑터가 없습니다. " +
                "${StoryEventsProperties.PREFIX}.enabled를 켜거나 ${StoryOutboxRelayProperties.PREFIX}.enabled를 끄십시오"
    }
}
