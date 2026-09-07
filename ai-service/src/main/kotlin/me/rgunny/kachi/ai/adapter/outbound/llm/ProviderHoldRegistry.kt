package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmHold
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 제공자 단위 보류 상태. 같은 제공자의 모델 가드들이 공유한다.
 *
 * 401·402·403은 모델이 아니라 계정의 문제라 한 모델에서 받아도 그 제공자의 모든 모델이 같은 답을 준다.
 * 가드마다 따로 들고 있으면 모델 수만큼 같은 실패를 반복하므로 한 곳에 둔다.
 * 만료된 보류는 조회 시 없는 것으로 보고, 갱신은 더 늦은 시각만 반영한다.
 */
class ProviderHoldRegistry {
    private val holds = ConcurrentHashMap<LlmProvider, LlmHold>()

    fun hold(provider: LlmProvider, code: LlmFailureCode, until: Instant): LlmHold {
        return holds.compute(provider) { _, current ->
            if (current == null || until.isAfter(current.until)) LlmHold(code, until) else current
        }!!
    }

    fun holdOf(provider: LlmProvider, now: Instant): LlmHold? = holds[provider]?.takeIf { it.isActive(now) }

    fun release(provider: LlmProvider) {
        holds.remove(provider)
    }
}
