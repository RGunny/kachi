package me.rgunny.kachi.ai.config

import java.time.Duration

/**
 * rate limit 응답을 받은 모델을 얼마나 쉬게 할지 정하는 설정.
 *
 * 제공자가 Retry-After로 지시한 값을 우선하되 [max]를 넘지 않는다.
 * 헤더가 없으면 [default]를 적용한다. 0으로 두면 같은 tick 안에서 같은 모델을 다시 골라 같은 응답을 받는다.
 */
data class LlmCooldownProperties(
    val default: Duration,
    val max: Duration
) {
    init {
        require(!default.isNegative && !default.isZero) {
            "cooldown 기본값은 양수여야 합니다"
        }
        require(max >= default) {
            "cooldown 최대값은 기본값 이상이어야 합니다"
        }
    }
}
