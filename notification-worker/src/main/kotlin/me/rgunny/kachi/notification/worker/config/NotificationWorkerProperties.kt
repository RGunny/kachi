package me.rgunny.kachi.notification.worker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * notification-worker 런타임 설정.
 */
@ConfigurationProperties(prefix = "kachi.notification.worker")
data class NotificationWorkerProperties(
    /**
     * DB claim의 claimedBy에 기록되는 worker 식별자.
     */
    val workerId: String = "notification-worker-local",
    val dispatch: Dispatch = Dispatch(),
    val sender: Sender = Sender(),
) {

    data class Dispatch(
        /**
         * notification-service outbox publisher가 발행하는 worker 실행 topic.
         */
        val topic: String = "notification.dispatch",

        /**
         * dispatch consumer group.
         * 같은 group 안에서는 Kafka partition 단위로 메시지를 분산 처리한다.
         */
        val groupId: String = "notification-worker",

        /**
         * Kafka at-least-once, rebalance, retry topic 재전달 중 같은 notificationId가 중복 처리되는 것을 줄이는 Redis 1차 가드 TTL.
         * 최종 중복 방지는 DB 상태 claim이 담당한다.
         */
        val dedupeTtl: Duration = Duration.ofMinutes(5),

        /**
         * vendor 호출 시 재사용할 idempotency key TTL.
         * 같은 알림을 retry할 때 외부 vendor가 중복 발송을 막을 수 있도록 notificationId 기준 key를 유지한다.
         */
        val idempotencyKeyTtl: Duration = Duration.ofHours(24),
        val retry: Retry = Retry(),
        val dlt: Dlt = Dlt(),
    ) {

        data class Retry(
            /**
             * 전체 dispatch 시도 횟수.
             * 최초 consume 1회를 포함하므로 Kafka FixedBackOff에는 maxAttempts - 1을 재시도 횟수로 전달한다.
             */
            val maxAttempts: Long = 3,

            /**
             * Kafka listener 레벨 retry 간격.
             * core RetryPolicy도 같은 값으로 조립해 Notification 상태와 Kafka retry 횟수가 어긋나지 않게 한다.
             */
            val backoff: Duration = Duration.ofSeconds(1),
        )

        data class Dlt(
            /**
             * 자동 재시도 종료 또는 malformed dispatch payload를 보관하는 topic.
             * DLT 영속화/운영자 재처리는 후속 admin/service 기능에서 담당한다.
             */
            val topic: String = "notification.dispatch.dlt",
        )
    }

    data class Sender(
        val mock: Mock = Mock(),
        val slack: Slack = Slack(),
    ) {

        data class Mock(
            /**
             * 실제 vendor adapter가 붙기 전 worker dispatch 흐름을 검증하기 위한 mock sender 활성화 여부.
             * 운영 환경에서는 false로 두고 실제 채널 sender bean만 사용해야 한다.
             */
            val enabled: Boolean = true,

            /**
             * mock sender가 지원할 채널 목록.
             */
            val channels: List<String> = listOf("SLACK", "DISCORD", "TELEGRAM", "SMS", "KAKAO", "EMAIL"),

            /**
             * mock sender 결과 모드.
             * SUCCESS, TRANSIENT_FAILURE, RATE_LIMITED, PERMANENT_FAILURE 중 하나를 사용한다.
             */
            val mode: String = "SUCCESS",
        )

        data class Slack(
            /**
             * Slack incoming webhook sender 활성화 여부.
             * true이면 mock sender가 SLACK을 지원하도록 설정되어 있어도 worker config가 mock SLACK 지원을 자동 제외한다.
             */
            val enabled: Boolean = false,

            /**
             * Slack incoming webhook URL.
             * secret 성격의 값이므로 운영에서는 환경변수나 secret manager로 주입한다.
             */
            val webhookUrl: String = "",

            /**
             * Slack webhook 호출 timeout.
             */
            val timeout: Duration = Duration.ofSeconds(3),
        )
    }
}
