package me.rgunny.kachi.ai.adapter.inbound.scheduler

import me.rgunny.kachi.ai.application.port.inbound.news.model.WatermarkSummaryWindowRequest
import java.time.Duration

/**
 * 뉴스 요약 scheduler가 보는 실행 설정.
 *
 * `kachi.ai.scheduler.news-summary`
 */
data class AiNewsSummarySchedulerSettings(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val overlap: Duration,
    val maxLookback: Duration,
    val maxArticlesPerKeyword: Int
) {
    companion object {
        const val PREFIX = "kachi.ai.scheduler.news-summary"

        // @Scheduled 애노테이션 인자용 placeholder 식(상수 문자열만 허용)
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }

    fun toWindowRequest(): WatermarkSummaryWindowRequest {
        return WatermarkSummaryWindowRequest(
            overlap = overlap,
            maxLookback = maxLookback
        )
    }
}
