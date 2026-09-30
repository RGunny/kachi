package me.rgunny.kachi.ai.adapter.outbound.llm

import java.time.Duration

/**
 * 구성상 장애로 보류한 모델과 제공자를 언제 다시 시도할지 정하는 설정.
 *
 * 404·401·402·403은 한 건으로 확정이라 서킷의 대기 시간으로 다루지 않는다.
 * [reprobeAfter] 뒤 한 번 다시 시도하고, 같은 실패면 다시 보류한다. 일일 한도처럼 시간이 풀어 주는 경우를 스스로 반영하기 위한 값이다.
 */
data class LlmHoldSettings(
    val reprobeAfter: Duration
) {
    init {
        require(!reprobeAfter.isNegative && !reprobeAfter.isZero) {
            "hold reprobe-after는 양수여야 합니다"
        }
    }
}
