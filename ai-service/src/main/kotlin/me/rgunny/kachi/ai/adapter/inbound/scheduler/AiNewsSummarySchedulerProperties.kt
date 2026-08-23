package me.rgunny.kachi.ai.adapter.inbound.scheduler

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.inbound.news.model.WatermarkSummaryWindowRequest
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 주기 뉴스 요약 실행 설정.
 *
 * 요약 구간은 저장된 watermark에서 이어받으므로 실행 주기와 무관하게 결정된다.
 * overlap은 watermark에서 뒤로 물러나는 폭이고, maxLookback은 장기 정지 후 한 구간이 무한정 커지는 것을 막는 하한이다.
 */
@ConfigurationProperties(prefix = AiNewsSummarySchedulerProperties.PREFIX)
data class AiNewsSummarySchedulerProperties(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val overlap: Duration,
    val maxLookback: Duration,
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
        require(!overlap.isNegative) {
            "뉴스 요약 overlap은 음수일 수 없습니다"
        }
        require(!maxLookback.isZero && !maxLookback.isNegative) {
            "뉴스 요약 maxLookback은 0보다 커야 합니다"
        }
        // overlap이 maxLookback보다 크면 watermark를 따라가기도 전에 하한에 걸려 매 실행이 잘린 구간을 만든다.
        require(overlap < maxLookback) {
            "뉴스 요약 overlap은 maxLookback보다 짧아야 합니다"
        }
        require(maxArticlesPerKeyword in 1..SummarizeNewsCommand.MAX_ARTICLES_PER_KEYWORD) {
            "키워드별 최대 뉴스 개수는 1 이상 ${SummarizeNewsCommand.MAX_ARTICLES_PER_KEYWORD} 이하여야 합니다"
        }
    }

    fun toWindowRequest(): WatermarkSummaryWindowRequest {
        return WatermarkSummaryWindowRequest(
            overlap = overlap,
            maxLookback = maxLookback
        )
    }
}
