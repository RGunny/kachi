package me.rgunny.kachi.story.application.service.assembly

import java.time.Duration

/**
 * 기사를 story에 붙일지 정하는 임계값과 검색·재시도 한도.
 *
 * 판정 점수는 0~1 공간이다.
 */
data class AssemblyPolicy(
    val thetaHigh: Double,
    val thetaLow: Double,
    val thetaJudge: Double,
    val candidateLimit: Int,
    val recentArticles: Int,
    val candidateWindow: Duration,
    val maxArticles: Int,
    val maxCasRetries: Int
) {
    init {
        require(thetaLow in 0.0..1.0) { "theta-low는 0과 1 사이여야 합니다: $thetaLow" }
        require(thetaHigh in thetaLow..1.0) { "theta-high는 theta-low 이상 1 이하여야 합니다: high=$thetaHigh, low=$thetaLow" }
        require(thetaJudge in 0.0..1.0) { "theta-judge는 0과 1 사이여야 합니다: $thetaJudge" }
        require(candidateLimit >= 1) { "candidate-limit은 1 이상이어야 합니다: $candidateLimit" }
        require(recentArticles >= 1) { "recent-articles는 1 이상이어야 합니다: $recentArticles" }
        require(!candidateWindow.isNegative && !candidateWindow.isZero) { "candidate-window는 양수여야 합니다: $candidateWindow" }
        require(maxArticles >= 2) { "max-articles는 2 이상이어야 합니다: $maxArticles" }
        require(maxCasRetries >= 0) { "max-cas-retries는 0 이상이어야 합니다: $maxCasRetries" }
    }
}
