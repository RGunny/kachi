package me.rgunny.kachi.ai.adapter.`in`.scheduler

import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 주기 뉴스 요약 실행 설정.
 *
 * lookback은 매 tick이 요약 대상으로 삼을 뉴스 수집 기간의 길이다.
 * fixedDelay보다 크게 두어 tick 사이 경계에 걸친 뉴스가 누락되지 않게 한다.
 */
@ConfigurationProperties(prefix = AiNewsSummarySchedulerProperties.PREFIX)
data class AiNewsSummarySchedulerProperties(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val lookback: Duration,
    val maxArticlesPerKeyword: Int,
) {
    companion object {
        const val PREFIX = "kachi.ai.scheduler.news-summary"

        // @Scheduled는 애노테이션이라 주입된 properties 값을 참조할 수 없고 placeholder만 받는다.
        // 같은 설정 키를 두 곳에 문자열로 흩어두지 않도록 여기서 한 번만 선언한다.
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }

    init {
        require(!lookback.isZero && !lookback.isNegative) {
            "뉴스 요약 lookback은 0보다 커야 합니다"
        }
        require(maxArticlesPerKeyword in 1..SummarizeNewsCommand.MAX_ARTICLES_PER_KEYWORD) {
            "키워드별 최대 뉴스 개수는 1 이상 ${SummarizeNewsCommand.MAX_ARTICLES_PER_KEYWORD} 이하여야 합니다"
        }
    }

    /**
     * lookback이 실행 주기보다 짧으면 tick 사이에 수집된 뉴스가 어느 요약에도 포함되지 않는다.
     */
    fun hasWindowGap(): Boolean {
        return lookback < fixedDelay
    }
}
