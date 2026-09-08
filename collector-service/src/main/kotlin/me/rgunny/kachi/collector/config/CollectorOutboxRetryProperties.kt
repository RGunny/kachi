package me.rgunny.kachi.collector.config

import java.time.Duration

/**
 * outbox 발행 재시도 설정.
 *
 * 값 검증은 [me.rgunny.kachi.collector.domain.outbox.CollectorOutboxRetryPolicy]가 하고 여기서는 yaml 값을 옮기기만 한다.
 * 같은 규칙을 두 곳에 두면 한쪽만 고쳐졌을 때 갈라진다.
 */
data class CollectorOutboxRetryProperties(
    val maxAttempts: Int,
    val baseDelay: Duration,
    val maxDelay: Duration,
    val multiplier: Double
)
