package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import java.time.Instant

/**
 * provider 하나가 지금 호출 가능한지에 대한 스냅샷.
 *
 * [circuitBreakerState]는 회로 라이브러리의 상태 이름을 그대로 쓴다.
 * 같은 값을 enum으로 다시 정의하면 라이브러리가 상태를 늘렸을 때 조용히 어긋난다.
 * [cooldownUntil]은 지금 쉬는 중일 때의 종료 시각이고, 쉬는 중이 아니면 null이다.
 * [failureRate]와 [slowCallRate]의 -1.0은 최소 호출 수에 아직 닿지 않아 비율을 낼 수 없다는 뜻이다.
 */
data class LlmProviderStatus(
    val provider: LlmProviderName,
    val circuitBreakerState: String,
    val cooldownUntil: Instant?,
    val failureRate: Float,
    val slowCallRate: Float,
    val bufferedCalls: Int,
    val successfulCalls: Int,
    val failedCalls: Int,
    val notPermittedCalls: Long
)
