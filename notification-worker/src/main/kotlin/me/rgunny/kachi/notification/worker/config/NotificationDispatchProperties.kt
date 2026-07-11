package me.rgunny.kachi.notification.worker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * notification.dispatch consume, retry, recovery 설정.
 */
@ConfigurationProperties(prefix = "kachi.notification.dispatch")
data class NotificationDispatchProperties(
    /**
     * notification-service outbox publisher가 발행하는 worker 실행 topic.
     */
    val topic: String,

    /**
     * dispatch consumer group.
     * 같은 group 안에서는 Kafka partition 단위로 메시지를 분산 처리한다.
     */
    val groupId: String,

    /**
     * Kafka at-least-once, rebalance, retry topic 재전달 중 같은 notificationId가 중복 처리되는 것을 줄이는 Redis 1차 가드 TTL.
     * 최종 중복 방지는 DB 상태 claim이 담당한다.
     */
    val dedupeTtl: Duration,

    /**
     * vendor 호출 시 재사용할 idempotency key TTL.
     * 같은 알림을 retry할 때 외부 vendor가 중복 발송을 막을 수 있도록 notificationId 기준 key를 유지한다.
     */
    val idempotencyKeyTtl: Duration,

    /**
     * PROCESSING 상태로 claim된 뒤 이 시간을 넘기면 worker가 중단된 것으로 보고 recovery 대상에 포함한다.
     */
    val processingVisibilityTimeout: Duration = Duration.ofSeconds(30),

    val recovery: Recovery = Recovery(
        enabled = true,
        interval = Duration.ofSeconds(30),
        batchSize = 100,
    ),
    val retry: Retry,
    val dlt: Dlt,
) {

    data class Recovery(
        /**
         * PROCESSING 상태로 멈춘 알림을 회수하는 scheduler 활성화 여부.
         */
        val enabled: Boolean,

        /**
         * stale PROCESSING 회수 주기.
         */
        val interval: Duration,

        /**
         * 한 번의 recovery tick에서 조회할 최대 알림 수.
         */
        val batchSize: Int,
    )

    data class Retry(
        /**
         * 전체 dispatch 시도 횟수.
         * 최초 consume 1회를 포함하므로 Kafka FixedBackOff에는 maxAttempts - 1을 재시도 횟수로 전달한다.
         */
        val maxAttempts: Long,

        /**
         * Kafka listener 레벨 retry 간격.
         * core RetryPolicy도 같은 값으로 조립해 Notification 상태와 Kafka retry 횟수가 어긋나지 않게 한다.
         */
        val backoff: Duration,
    )

    data class Dlt(
        /**
         * 자동 재시도 종료 또는 malformed dispatch payload를 보관하는 topic.
         * DLT 영속화/운영자 재처리는 후속 admin/service 기능에서 담당한다.
         */
        val topic: String,
    )
}
