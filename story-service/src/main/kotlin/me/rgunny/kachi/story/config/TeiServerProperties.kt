package me.rgunny.kachi.story.config

import java.time.Duration

/**
 * 추론 서버 하나의 주소와 시간 설정.
 *
 * [slowAfter]는 서킷 브레이커의 slow call duration threshold이고 [timeout]보다 짧아야 한다.
 */
data class TeiServerProperties(
    val baseUrl: String,
    val connectTimeout: Duration,
    val timeout: Duration,
    val slowAfter: Duration
) {
    init {
        require(baseUrl.isNotBlank()) { "TEI base-url은 비어 있을 수 없습니다" }
        require(!connectTimeout.isNegative && !connectTimeout.isZero) { "TEI connect-timeout은 양수여야 합니다" }
        require(!slowAfter.isNegative && !slowAfter.isZero) { "TEI slow-after는 양수여야 합니다" }
        require(slowAfter < timeout) { "TEI slow-after는 timeout보다 짧아야 합니다" }
    }
}
