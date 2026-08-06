package me.rgunny.kachi.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 키워드 격리 정책 설정.
 *
 * 임계치는 scheduler 실행뿐 아니라 internal API 실행에도 같이 적용되므로 scheduler 설정이 아니라 별도로 둔다.
 */
@ConfigurationProperties(prefix = KeywordQuarantineProperties.PREFIX)
data class KeywordQuarantineProperties(
    val failureThreshold: Int
) {
    companion object {
        const val PREFIX = "kachi.ai.quarantine"
    }

    init {
        require(failureThreshold >= 1) {
            "격리 임계치는 1 이상이어야 합니다"
        }
    }
}
