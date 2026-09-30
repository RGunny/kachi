package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.application.service.outbox.AiOutboxRelayPolicy
import me.rgunny.kachi.ai.domain.outbox.AiOutboxRetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * outbox relay 실행 설정.
 *
 * [enabled]가 false면 relay를 구성하는 빈이 아예 만들어지지 않고 이벤트는 발행 대기 상태로 쌓인다.
 * 이 스위치는 outbox 행을 읽어 발행 포트로 넘기는 relay의 것이다. 어디로 보낼지는 발행 포트 구현 쪽 설정이 정한다.
 * 이 설정 자체는 켜기 전에도 값이 유효한지 확인해야 하므로 항상 바인딩된다.
 */
@ConfigurationProperties(prefix = AiOutboxRelayProperties.PREFIX)
data class AiOutboxRelayProperties(
    val enabled: Boolean,
    val publisherId: String,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val batchSize: Int,
    val publishingVisibilityTimeout: Duration,
    val retry: AiOutboxRetryProperties
) {
    companion object {
        const val PREFIX = "kachi.ai.outbox.relay"
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

    fun toPolicy(): AiOutboxRelayPolicy {
        return AiOutboxRelayPolicy(
            batchSize = batchSize,
            publisherId = publisherId,
            retryPolicy = toRetryPolicy(),
            publishingVisibilityTimeout = publishingVisibilityTimeout
        )
    }

    fun toRetryPolicy(): AiOutboxRetryPolicy {
        return AiOutboxRetryPolicy(
            maxAttempts = retry.maxAttempts,
            baseDelay = retry.baseDelay,
            maxDelay = retry.maxDelay,
            multiplier = retry.multiplier
        )
    }
}
