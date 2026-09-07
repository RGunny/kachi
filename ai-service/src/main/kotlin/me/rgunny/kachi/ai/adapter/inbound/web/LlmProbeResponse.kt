package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProbeResult

/**
 * 운영자가 지정한 모델 하나에 실제 요청을 한 번 보낸 결과의 응답.
 *
 * [model]은 상수명이고 [requestedModel]은 요청에 실은 wire id, [servedModel]은 응답이 보고한 모델이다.
 * [billing]이 과금 계정이면 이 호출은 과금됐다. [latencyMillis]는 가드 통과부터 응답 파싱까지다.
 */
data class LlmProbeResponse(
    val model: String,
    val provider: String,
    val billing: String,
    val requestedModel: String,
    val servedModel: String,
    val promptVersion: String,
    val latencyMillis: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val expandedKeywords: List<String>
) {
    companion object {

        fun from(result: LlmProbeResult): LlmProbeResponse {
            return LlmProbeResponse(
                model = result.model.name,
                provider = result.model.provider.code,
                billing = result.billing.name,
                requestedModel = result.metadata.requestedModel,
                servedModel = result.metadata.model,
                promptVersion = result.metadata.promptVersion.value,
                latencyMillis = result.latency.toMillis(),
                inputTokens = result.metadata.tokenUsage.inputTokens,
                outputTokens = result.metadata.tokenUsage.outputTokens,
                expandedKeywords = result.expandedKeywords.map { it.value }
            )
        }
    }
}
