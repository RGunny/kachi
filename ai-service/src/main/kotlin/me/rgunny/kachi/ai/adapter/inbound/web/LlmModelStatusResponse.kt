package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import java.time.Instant

/**
 * 모델 하나의 차단 상태 응답.
 * [model]은 상수명, [provider]는 제공자 code.
 */
data class LlmModelStatusResponse(
    val model: String,
    val provider: String,
    val circuitBreakerState: String,
    val cooldownUntil: Instant?,
    val failureRate: Float,
    val slowCallRate: Float,
    val bufferedCalls: Int,
    val successfulCalls: Int,
    val failedCalls: Int,
    val notPermittedCalls: Long
) {
    companion object {

        fun from(status: LlmModelStatus): LlmModelStatusResponse {
            return LlmModelStatusResponse(
                model = status.model.name,
                provider = status.model.provider.code,
                circuitBreakerState = status.circuitBreakerState,
                cooldownUntil = status.cooldownUntil,
                failureRate = status.failureRate,
                slowCallRate = status.slowCallRate,
                bufferedCalls = status.bufferedCalls,
                successfulCalls = status.successfulCalls,
                failedCalls = status.failedCalls,
                notPermittedCalls = status.notPermittedCalls
            )
        }
    }
}
