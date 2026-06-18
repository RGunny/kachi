package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.InvalidDispatchMessageException
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * notification.dispatch topic 전용 Kafka listener runtime 설정.
 *
 * worker는 core dispatch 결과가 RETRYABLE일 때만 예외를 던져 Kafka retry/DLT 흐름을 탄다.
 * payload 자체가 잘못된 경우는 재시도해도 성공할 수 없으므로 즉시 DLT로 보낸다.
 */
@Configuration
class NotificationDispatchKafkaConfig {

    /**
     * dispatch listener 전용 container factory.
     *
     * service의 notification.requested listener와 worker의 notification.dispatch listener는 실패 지점과 retry 의미가 다르므로
     * 별도 factory/error handler로 분리한다.
     */
    @Bean
    fun notificationDispatchKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        notificationDispatchErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(notificationDispatchErrorHandler)
        }
    }

    /**
     * dispatch 실패를 Kafka retry/DLT로 연결하는 error handler.
     *
     * RetryableDispatchMessageException은 core 결과가 RETRYABLE일 때 listener가 던지는 예외이며,
     * 여기서는 retryable 기본 예외로 두어 FixedBackOff 후 DLT로 이동하게 한다.
     * InvalidDispatchMessageException은 payload 자체가 잘못된 경우라 재시도 없이 DLT로 보낸다.
     */
    @Bean
    fun notificationDispatchErrorHandler(
        kafkaTemplate: KafkaOperations<String, String>,
        properties: NotificationWorkerProperties,
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition(properties.dispatch.dlt.topic, record.partition())
        }
        val retry = properties.dispatch.retry
        val backOff = FixedBackOff(
            retry.backoff.toMillis(),
            (retry.maxAttempts - 1).coerceAtLeast(0),
        )

        return DefaultErrorHandler(recoverer, backOff).apply {
            // malformed payload는 같은 record를 다시 받아도 성공하지 않으므로 즉시 DLT로 보낸다.
            addNotRetryableExceptions(InvalidDispatchMessageException::class.java)
            // recoverer가 DLT publish에 성공한 record는 offset을 commit해 같은 malformed/dead 메시지의 무한 재처리를 막는다.
            setCommitRecovered(true)
        }
    }
}
