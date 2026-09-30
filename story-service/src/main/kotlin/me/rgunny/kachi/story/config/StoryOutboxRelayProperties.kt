package me.rgunny.kachi.story.config

import java.time.Duration
import me.rgunny.kachi.story.application.service.outbox.StoryOutboxRelayPolicy
import me.rgunny.kachi.story.domain.outbox.StoryOutboxRetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * outbox relay 실행 설정.
 *
 * [enabled]가 false면 relay를 구성하는 빈이 아예 만들어지지 않고 이벤트는 발행 대기 상태로 쌓인다.
 */
@ConfigurationProperties(prefix = StoryOutboxRelayProperties.PREFIX)
data class StoryOutboxRelayProperties(
    val enabled: Boolean,
    val publisherId: String,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val batchSize: Int,
    val publishingVisibilityTimeout: Duration,
    val retry: Retry
) {
    companion object {
        const val PREFIX = "kachi.story.outbox.relay"
    }

    init {
        require(publisherId.isNotBlank()) { "outbox relay publisher id는 비어 있을 수 없습니다" }
        require(!fixedDelay.isNegative && !fixedDelay.isZero) { "outbox relay 실행 주기는 양수여야 합니다: $fixedDelay" }
        require(!initialDelay.isNegative) { "outbox relay 최초 실행 지연은 음수일 수 없습니다: $initialDelay" }
        require(batchSize >= 1) { "outbox relay batch 크기는 1 이상이어야 합니다: $batchSize" }
        require(!publishingVisibilityTimeout.isNegative && !publishingVisibilityTimeout.isZero) {
            "outbox relay 발행 점유 유효 시간은 양수여야 합니다: $publishingVisibilityTimeout"
        }
    }

    /**
     * 재시도 값. 규칙 검증은 [StoryOutboxRetryPolicy]가 하고 여기서는 yaml 값을 옮기기만 한다.
     */
    data class Retry(
        val maxAttempts: Int,
        val baseDelay: Duration,
        val maxDelay: Duration,
        val multiplier: Double
    )

    fun toPolicy(): StoryOutboxRelayPolicy {
        return StoryOutboxRelayPolicy(
            batchSize = batchSize,
            publisherId = publisherId,
            retryPolicy = toRetryPolicy(),
            publishingVisibilityTimeout = publishingVisibilityTimeout
        )
    }

    fun toRetryPolicy(): StoryOutboxRetryPolicy {
        return StoryOutboxRetryPolicy(
            maxAttempts = retry.maxAttempts,
            baseDelay = retry.baseDelay,
            maxDelay = retry.maxDelay,
            multiplier = retry.multiplier
        )
    }
}
