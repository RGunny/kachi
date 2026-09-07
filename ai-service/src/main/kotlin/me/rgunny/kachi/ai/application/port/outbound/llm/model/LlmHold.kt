package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import java.time.Instant

/**
 * 구성상 장애로 모델이나 제공자를 한동안 후보에서 뺀 상태.
 *
 * [code]는 보류를 건 실패이고 [until]은 다시 한 번 시도해 볼 시각이다.
 * 서킷과 달리 표본이 아니라 한 건으로 확정되고, 대기 시간 뒤 풀리는 것이 아니라 재탐색 한 번을 허용한다.
 */
data class LlmHold(
    val code: LlmFailureCode,
    val until: Instant
) {
    fun isActive(now: Instant): Boolean = now < until
}
