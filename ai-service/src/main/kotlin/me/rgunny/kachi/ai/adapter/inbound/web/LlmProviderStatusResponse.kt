package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProviderStatus
import java.time.Instant

data class LlmProviderStatusResponse(
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

        fun from(status: LlmProviderStatus): LlmProviderStatusResponse {
            return LlmProviderStatusResponse(
                provider = status.provider.value,
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
