package me.rgunny.kachi.collector.config

import me.rgunny.kachi.collector.application.service.outbox.CollectorOutboxRelayPolicy
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxRetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * outbox relay 실행 설정.
 *
 * [enabled]가 false면 relay를 구성하는 빈이 아예 만들어지지 않고 이벤트는 발행 대기 상태로 쌓인다.
 * 이 스위치는 outbox 행을 읽어 발행 포트로 넘기는 relay의 것이다. 어디로 보낼지는 발행 포트 구현 쪽 설정이 정한다.
 * 이 설정 자체는 켜기 전에도 값이 유효한지 확인해야 하므로 항상 바인딩된다.
 */
@ConfigurationProperties(prefix = CollectorOutboxRelayProperties.PREFIX)
data class CollectorOutboxRelayProperties(
    val enabled: Boolean,
    val publisherId: String,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val batchSize: Int,
    val publishingVisibilityTimeout: Duration,
    val retry: CollectorOutboxRetryProperties
) {
    companion object {
        const val PREFIX = "kachi.collector.outbox.relay"

        // @Scheduled는 애노테이션이라 주입된 properties 값을 참조할 수 없고 placeholder만 받는다.
        // 같은 설정 키를 두 곳에 문자열로 흩어두지 않도록 여기서 한 번만 선언한다.
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }

    init {
        require(publisherId.isNotBlank()) {
            "outbox relay publisher id는 비어 있을 수 없습니다"
        }
        require(!fixedDelay.isNegative && !fixedDelay.isZero) {
            "outbox relay 실행 주기는 양수여야 합니다"
        }
        require(!initialDelay.isNegative) {
            "outbox relay 최초 실행 지연은 음수일 수 없습니다"
        }
        require(batchSize >= 1) {
            "outbox relay batch 크기는 1 이상이어야 합니다"
        }
        require(!publishingVisibilityTimeout.isNegative && !publishingVisibilityTimeout.isZero) {
            "outbox relay 발행 점유 유효 시간은 양수여야 합니다"
        }
    }

    fun toPolicy(): CollectorOutboxRelayPolicy {
        return CollectorOutboxRelayPolicy(
            batchSize = batchSize,
            publisherId = publisherId,
            retryPolicy = toRetryPolicy(),
            publishingVisibilityTimeout = publishingVisibilityTimeout
        )
    }

    fun toRetryPolicy(): CollectorOutboxRetryPolicy {
        return CollectorOutboxRetryPolicy(
            maxAttempts = retry.maxAttempts,
            baseDelay = retry.baseDelay,
            maxDelay = retry.maxDelay,
            multiplier = retry.multiplier
        )
    }
}
