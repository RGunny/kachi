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
    val workerId: String,
    val dispatch: Dispatch,
    val sender: Sender,
) {

    data class Dispatch(
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
        val retry: Retry,
        val dlt: Dlt,
    ) {

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

    data class Sender(
        val mock: Mock,
        val slack: Slack,
        val discord: Discord,
        val telegram: Telegram,
    ) {

        data class Mock(
            /**
             * 실제 vendor adapter가 붙기 전 worker dispatch 흐름을 검증하기 위한 mock sender 활성화 여부.
             * 운영 환경에서는 false로 두고 실제 채널 sender bean만 사용해야 한다.
             */
            val enabled: Boolean,

            /**
             * mock sender가 지원할 채널 목록.
             */
            val channels: List<String>,

            /**
             * mock sender 결과 모드.
             * SUCCESS, TRANSIENT_FAILURE, RATE_LIMITED, PERMANENT_FAILURE 중 하나를 사용한다.
             */
            val mode: String,
        )

        data class Slack(
            /**
             * Slack incoming webhook sender 활성화 여부.
             * true이면 mock sender가 SLACK을 지원하도록 설정되어 있어도 worker config가 mock SLACK 지원을 자동 제외한다.
             */
            val enabled: Boolean,

            /**
             * Slack incoming webhook URL.
             * secret 성격의 값이므로 운영에서는 환경변수나 secret manager로 주입한다.
             */
            val webhookUrl: String?,

            /**
             * Slack host와 TCP 연결을 맺을 때의 대기 상한.
             */
            val connectTimeout: Duration,

            /**
             * 요청을 보낸 뒤 첫 응답을 기다리는 대기 상한.
             */
            val responseTimeout: Duration,

            /**
             * 응답 body read가 멈췄을 때의 대기 상한.
             */
            val readTimeout: Duration,

            /**
             * webhook 요청 body write가 멈췄을 때의 대기 상한.
             */
            val writeTimeout: Duration,

            /**
             * Slack 응답 body 버퍼링 상한.
             */
            val maxInMemorySize: Int,
        )

        data class Discord(
            /**
             * Discord incoming webhook sender 활성화 여부.
             * true이면 mock sender가 DISCORD를 지원하도록 설정되어 있어도 worker config가 mock DISCORD 지원을 자동 제외한다.
             */
            val enabled: Boolean,

            /**
             * Discord incoming webhook URL.
             * secret 성격의 값이므로 운영에서는 환경변수나 secret manager로 주입한다.
             */
            val webhookUrl: String?,

            /**
             * Discord host와 TCP 연결을 맺을 때의 대기 상한.
             */
            val connectTimeout: Duration,

            /**
             * 요청을 보낸 뒤 첫 응답을 기다리는 대기 상한.
             */
            val responseTimeout: Duration,

            /**
             * 응답 body read가 멈췄을 때의 대기 상한.
             */
            val readTimeout: Duration,

            /**
             * webhook 요청 body write가 멈췄을 때의 대기 상한.
             */
            val writeTimeout: Duration,

            /**
             * Discord 응답 body 버퍼링 상한.
             */
            val maxInMemorySize: Int,
        )

        data class Telegram(
            /**
             * Telegram Bot API sender 활성화 여부.
             * true이면 mock sender가 TELEGRAM을 지원하도록 설정되어 있어도 worker config가 mock TELEGRAM 지원을 자동 제외한다.
             */
            val enabled: Boolean,

            /**
             * Telegram Bot API base URL.
             */
            val baseUrl: String,

            /**
             * Telegram bot token.
             * secret 성격의 값이므로 운영에서는 환경변수나 secret manager로 주입한다.
             */
            val botToken: String?,

            /**
             * Telegram sendMessage API path.
             */
            val sendMessagePath: String,

            /**
             * Telegram host와 TCP 연결을 맺을 때의 대기 상한.
             */
            val connectTimeout: Duration,

            /**
             * 요청을 보낸 뒤 첫 응답을 기다리는 대기 상한.
             */
            val responseTimeout: Duration,

            /**
             * 응답 body read가 멈췄을 때의 대기 상한.
             */
            val readTimeout: Duration,

            /**
             * sendMessage 요청 body write가 멈췄을 때의 대기 상한.
             */
            val writeTimeout: Duration,

            /**
             * Telegram 응답 body 버퍼링 상한.
             */
            val maxInMemorySize: Int,
        )
    }
}
