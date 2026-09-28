package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.adapter.inbound.messaging.exception.InvalidStoryEventMessageException
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

/**
 * story 이벤트를 읽는 listener 전용 Kafka runtime 설정.
 *
 * 메시지 자체의 결함은 즉시 DLT로 보내고 offset을 커밋한다.
 * LLM·저장소·경합 실패는 상한 없는 backoff로 같은 offset을 다시 처리하며, 메시지는 DLT로 빠지지 않고 lag로 쌓인다.
 * offset은 레코드마다 커밋한다.
 */
@Configuration
class StoryEventConsumerKafkaConfig {

    @Bean
    fun storyEventKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        storyEventConsumerErrorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(storyEventConsumerErrorHandler)
            // 레코드마다 offset 커밋
            containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        }
    }

    @Bean
    fun storyEventConsumerErrorHandler(
        kafkaTemplate: KafkaOperations<String, String>,
        properties: StoryEventConsumerProperties
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition(properties.dlt.topic, record.partition())
        }

        return DefaultErrorHandler(recoverer, storyEventConsumerBackOff(properties.retry)).apply {
            addNotRetryableExceptions(InvalidStoryEventMessageException::class.java)
            setCommitRecovered(true)
        }
    }

    /**
     * 상한 없는 지수 backoff.
     *
     * `maxElapsedTime`은 기본값(무제한)이다(상한에 닿으면 recoverer로 넘어감).
     */
    fun storyEventConsumerBackOff(retry: StoryEventConsumerProperties.Retry): ExponentialBackOff {
        return ExponentialBackOff(retry.initialBackoff.toMillis(), retry.multiplier).apply {
            maxInterval = retry.maxBackoff.toMillis()
        }
    }
}
