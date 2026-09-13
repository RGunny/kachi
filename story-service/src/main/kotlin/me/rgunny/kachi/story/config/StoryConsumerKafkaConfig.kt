package me.rgunny.kachi.story.config

import me.rgunny.kachi.story.adapter.inbound.messaging.exception.InvalidNewsCollectedMessageException
import org.apache.kafka.common.TopicPartition
import org.springframework.boot.context.properties.EnableConfigurationProperties
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
 * 기사 이벤트를 읽는 listener 전용 Kafka runtime 설정.
 *
 * 기사 자체의 결함은 즉시 DLT로 보내고 offset을 커밋한다.
 * 추론 서버·색인·저장소·경합 실패는 상한 없는 backoff로 같은 offset을 다시 처리하며, 기사는 DLT로 빠지지 않고 lag로 쌓인다.
 * offset은 레코드마다 커밋한다.
 */
@Configuration
@EnableConfigurationProperties(StoryConsumerProperties::class)
class StoryConsumerKafkaConfig {

    @Bean
    fun storyConsumerKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        storyConsumerErrorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(storyConsumerErrorHandler)
            // 레코드마다 offset 커밋
            containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        }
    }

    @Bean
    fun storyConsumerErrorHandler(
        kafkaTemplate: KafkaOperations<String, String>,
        properties: StoryConsumerProperties
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition(properties.dlt.topic, record.partition())
        }

        return DefaultErrorHandler(recoverer, storyConsumerBackOff(properties.retry)).apply {
            addNotRetryableExceptions(InvalidNewsCollectedMessageException::class.java)
            setCommitRecovered(true)
        }
    }

    /**
     * 상한 없는 지수 backoff. `maxElapsedTime`을 두지 않아 recoverer로 넘어가지 않는다.
     */
    fun storyConsumerBackOff(retry: StoryConsumerProperties.Retry): ExponentialBackOff {
        return ExponentialBackOff(retry.initialBackoff.toMillis(), retry.multiplier).apply {
            maxInterval = retry.maxBackoff.toMillis()
        }
    }
}
