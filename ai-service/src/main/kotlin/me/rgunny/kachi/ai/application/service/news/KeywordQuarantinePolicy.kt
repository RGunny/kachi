package me.rgunny.kachi.ai.application.service.news

/**
 * 키워드를 격리로 넘기는 연속 실패 임계치.
 *
 * `kachi.ai.quarantine`
 */
data class KeywordQuarantinePolicy(
    val failureThreshold: Int
)
