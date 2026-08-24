package me.rgunny.kachi.ai.application.service.outbox

import me.rgunny.kachi.ai.domain.outbox.AiOutboxRetryPolicy
import java.time.Duration

/**
 * relay tick 한 번이 지키는 정책.
 *
 * [publishingVisibilityTimeout]은 발행 중인 행을 죽은 것으로 보기까지 기다리는 시간이다.
 * 한 tick이 이 시간보다 오래 걸리면 자기가 잡고 있는 행을 다음 tick이 회수하므로
 * `batchSize × 발행 1건 timeout`이 이 값보다 짧아야 한다.
 */
data class AiOutboxRelayPolicy(
    val batchSize: Int,
    val publisherId: String,
    val retryPolicy: AiOutboxRetryPolicy,
    val publishingVisibilityTimeout: Duration
)
