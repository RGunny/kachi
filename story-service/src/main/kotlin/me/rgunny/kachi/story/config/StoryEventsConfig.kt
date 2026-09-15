package me.rgunny.kachi.story.config

import me.rgunny.kachi.story.adapter.outbound.outbox.KafkaStoryOutboxPublisher
import me.rgunny.kachi.story.adapter.outbound.outbox.StoryEventTopics
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPublisherPort
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate

/**
 * 이벤트 발행이 켜져 있을 때만 Kafka 발행 어댑터와 topic 선언을 조립하는 설정.
 */
@Configuration
@ConditionalOnProperty(
    prefix = StoryEventsProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true"
)
class StoryEventsConfig {

    @Bean
    fun kafkaStoryOutboxPublisher(
        kafkaTemplate: KafkaTemplate<String, String>,
        properties: StoryEventsProperties
    ): StoryOutboxPublisherPort {
        return KafkaStoryOutboxPublisher(
            kafkaTemplate = kafkaTemplate,
            topics = StoryEventTopics(
                articleAttached = properties.topics.articleAttached,
                merged = properties.topics.merged
            )
        )
    }

    @Bean
    fun storyArticleAttachedTopic(properties: StoryEventsProperties): NewTopic {
        return retainedTopic(properties.topics.articleAttached, properties)
    }

    @Bean
    fun storyMergedTopic(properties: StoryEventsProperties): NewTopic {
        return retainedTopic(properties.topics.merged, properties)
    }

    private fun retainedTopic(name: String, properties: StoryEventsProperties): NewTopic {
        return TopicBuilder.name(name)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
            .config(TopicConfig.RETENTION_MS_CONFIG, properties.retention.toMillis().toString())
            .build()
    }
}
