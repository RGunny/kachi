package me.rgunny.kachi.ai.config

import java.time.Duration

/**
 * rate limit 응답을 받은 provider를 얼마나 쉬게 할지 정하는 설정.
 *
 * provider가 Retry-After로 지시한 값을 우선하되 [maxCooldown]을 넘지 않는다.
 * 헤더가 없으면 [defaultCooldown]을 적용한다. 0으로 두면 같은 tick 안에서 같은 provider를 다시 골라
 * 같은 응답을 받는다.
 */
data class LlmFailoverProperties(
    val defaultCooldown: Duration,
    val maxCooldown: Duration
) {
    init {
        require(!defaultCooldown.isNegative && !defaultCooldown.isZero) {
            "failover 기본 cooldown은 양수여야 합니다"
        }
        require(maxCooldown >= defaultCooldown) {
            "failover 최대 cooldown은 기본 cooldown 이상이어야 합니다"
        }
    }
}
