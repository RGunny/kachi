package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmModel
import java.time.Duration

/**
 * 운영자가 지정한 모델 하나에 실제 요청을 한 번 보낸 결과.
 *
 * [billing]은 이 호출이 과금 계정으로 나갔는지를 결과에 남긴다. [latency]는 가드 통과부터 응답 파싱까지 걸린 시간이다.
 * 어느 모델이 실제로 답했는지는 [metadata]가 요청 모델과 응답 모델로 갖는다.
 */
data class LlmProbeResult(
    val model: LlmModel,
    val billing: LlmBilling,
    val metadata: LlmGenerationMetadata,
    val latency: Duration,
    val expandedKeywords: List<ExpandedKeyword>
)
