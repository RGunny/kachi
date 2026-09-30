package me.rgunny.kachi.story.adapter.outbound.tei

import me.rgunny.kachi.story.domain.inference.InferenceTarget

/**
 * 추론 서버 하나의 서킷 상태 스냅샷.
 *
 * [circuitBreakerState]는 회로 라이브러리의 상태 이름 그대로다.
 * [failureRate]와 [slowCallRate]의 -1.0은 최소 호출 수에 아직 닿지 않았다는 뜻이다.
 */
data class TeiClientStatus(
    val target: InferenceTarget,
    val circuitBreakerState: String,
    val failureRate: Float,
    val slowCallRate: Float,
    val bufferedCalls: Int,
    val successfulCalls: Int,
    val failedCalls: Int,
    val notPermittedCalls: Long
)
