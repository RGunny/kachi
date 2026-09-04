package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import java.time.Instant

/**
 * failover 순회의 후보.
 *
 * 호출 계약은 [LlmProviderPort]가 정하고, 여기서는 순회에 필요한 식별과 가용성 힌트만 더한다.
 * 가용성은 힌트일 뿐이라 틀려도 무해해야 한다. 실제 차단은 호출 시점에 후보 스스로
 * [LlmFailureCode.LLM_PROVIDER_UNAVAILABLE]로 실패시켜야 하고, 그 실패는 실제 호출로 세지 않는다.
 */
interface LlmProviderCandidate : LlmProviderPort {

    val model: LlmModel

    /** 지금 호출해 볼 만한 후보인지에 대한 힌트. 운영 조회에만 쓴다. */
    fun isLikelyAvailable(now: Instant): Boolean

    /** 호출되지 못한 이유. 후보가 전부 빠졌을 때 운영자에게 보여 줄 문자열이다. */
    fun blockedReason(now: Instant): String
}
