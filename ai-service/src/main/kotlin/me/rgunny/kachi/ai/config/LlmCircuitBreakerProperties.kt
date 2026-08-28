package me.rgunny.kachi.ai.config

import java.time.Duration

/**
 * provider별 서킷 브레이커 설정.
 *
 * [slowCallDurationThreshold]는 provider의 `response-timeout`보다 짧아야 timeout 전에 느려짐을 잡는다.
 * provider마다 timeout이 달라 여기서는 검증하지 않는다.
 */
data class LlmCircuitBreakerProperties(
    val slidingWindowSize: Int,
    val minimumNumberOfCalls: Int,
    val failureRateThreshold: Float,
    val slowCallDurationThreshold: Duration,
    val slowCallRateThreshold: Float,
    val waitDurationInOpenState: Duration,
    val permittedNumberOfCallsInHalfOpenState: Int
) {
    init {
        require(slidingWindowSize >= 1) {
            "서킷 브레이커 sliding window 크기는 1 이상이어야 합니다"
        }
        require(minimumNumberOfCalls in 1..slidingWindowSize) {
            "서킷 브레이커 최소 호출 수는 1 이상 sliding window 크기 이하여야 합니다"
        }
        require(failureRateThreshold > 0 && failureRateThreshold <= 100) {
            "서킷 브레이커 실패율 임계치는 0 초과 100 이하여야 합니다"
        }
        require(slowCallRateThreshold > 0 && slowCallRateThreshold <= 100) {
            "서킷 브레이커 느린 호출 비율 임계치는 0 초과 100 이하여야 합니다"
        }
        require(!slowCallDurationThreshold.isNegative && !slowCallDurationThreshold.isZero) {
            "서킷 브레이커 느린 호출 판정 시간은 양수여야 합니다"
        }
        require(!waitDurationInOpenState.isNegative && !waitDurationInOpenState.isZero) {
            "서킷 브레이커 open 상태 대기 시간은 양수여야 합니다"
        }
        require(permittedNumberOfCallsInHalfOpenState >= 1) {
            "서킷 브레이커 half-open 허용 호출 수는 1 이상이어야 합니다"
        }
    }
}
