package me.rgunny.kachi.story.config

import java.time.Duration

/** 추론 서버별 서킷 브레이커가 공유하는 failure rate·slow call rate·wait duration 정책. */
data class InferenceCircuitBreakerProperties(
    val slidingWindowSize: Int,
    val minimumNumberOfCalls: Int,
    val failureRateThreshold: Float,
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
        require(!waitDurationInOpenState.isNegative && !waitDurationInOpenState.isZero) {
            "서킷 브레이커 open 상태 대기 시간은 양수여야 합니다"
        }
        require(permittedNumberOfCallsInHalfOpenState >= 1) {
            "서킷 브레이커 half-open 허용 호출 수는 1 이상이어야 합니다"
        }
    }
}
