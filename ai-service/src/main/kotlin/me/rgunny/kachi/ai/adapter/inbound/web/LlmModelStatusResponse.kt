package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import java.time.Instant

/**
 * 모델 하나의 차단 상태 응답.
 * [model]은 상수명, [provider]는 제공자 code, [billing]은 그 제공자 계정의 과금 방식이다.
 * [holdReason]은 보류를 건 실패 코드이고 보류가 없으면 [holdUntil]과 함께 null이다.
 */
data class LlmModelStatusResponse(
    val model: String,
    val provider: String,
    val billing: String,
    val circuitBreakerState: String,
    val cooldownUntil: Instant?,
    val holdReason: String?,
    val holdUntil: Instant?,
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
                billing = status.billing.name,
                circuitBreakerState = status.circuitBreakerState,
                cooldownUntil = status.cooldownUntil,
                holdReason = status.hold?.code?.code,
                holdUntil = status.hold?.until,
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
