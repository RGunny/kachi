package me.rgunny.kachi.notification.service.config

import me.rgunny.kachi.notification.service.adapter.inbound.messaging.InvalidNotificationRequestedMessageException
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
 * notification.requested topic 전용 Kafka listener runtime 설정.
 *
 * 이 topic은 외부 bounded context가 notification-service에 알림 요청을 접수시키는 진입점이다.
 * 규격이 다른 payload나 지원하지 않는 schemaVersion은 같은 메시지를 재시도해도 성공할 수 없으므로 즉시 DLT로 보낸다.
 * Redis/Mongo 등 일시 장애로 request use case가 실패한 경우에는 짧게 재시도한 뒤 DLT로 보내 운영자가 원인을 확인할 수 있게 한다.
 */
@Configuration
class NotificationRequestedKafkaConfig {

    /**
     * notification.requested listener만 별도 error handler를 쓰도록 분리한다.
     *
     * worker의 notification.dispatch retry 정책은 vendor 발송 실패를 다루므로,
     * 요청 접수 실패를 다루는 이 factory와 retry/DLT 설정을 공유하지 않는다.
     */
    @Bean
    fun notificationRequestedKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        notificationRequestedErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(notificationRequestedErrorHandler)
        }
    }

    /**
     * 요청 접수 실패를 DLT로 보내는 error handler.
     *
     * FixedBackOff의 maxAttempts는 "재시도 횟수"라서, 설정의 maxAttempts가 전체 처리 시도 횟수를 의미하도록 1을 뺀다.
     * 예를 들어 maxAttempts=3이면 최초 처리 1회 + 재시도 2회 후 DLT로 이동한다.
     */
    @Bean
    fun notificationRequestedErrorHandler(
        kafkaTemplate: KafkaOperations<String, String>,
        properties: NotificationServiceProperties,
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition(properties.request.dlt.topic, record.partition())
        }
        val retry = properties.request.retry
        val backOff = FixedBackOff(
            retry.backoff.toMillis(),
            (retry.maxAttempts - 1).coerceAtLeast(0),
        )

        return DefaultErrorHandler(recoverer, backOff).apply {
            // payload/schema 문제는 메시지 자체가 잘못된 것이므로 broker 재전달로 해결되지 않는다.
            addNotRetryableExceptions(InvalidNotificationRequestedMessageException::class.java)
        }
    }
}
