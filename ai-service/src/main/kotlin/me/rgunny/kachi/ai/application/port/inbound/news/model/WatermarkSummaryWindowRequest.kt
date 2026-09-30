package me.rgunny.kachi.ai.application.port.inbound.news.model

import java.time.Duration

/**
 * 저장된 watermark에서 이어받는 요청.
 * 격리되지 않은 키워드가 모두 성공하면 watermark를 전진시킨다.
 */
data class WatermarkSummaryWindowRequest(
    val overlap: Duration,
    val maxLookback: Duration
) : SummaryWindowRequest {
    init {
        require(!overlap.isNegative) { "요약 window overlap은 음수일 수 없습니다" }
        require(!maxLookback.isZero && !maxLookback.isNegative) { "요약 window maxLookback은 0보다 커야 합니다" }
    }
}
