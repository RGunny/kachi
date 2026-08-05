package me.rgunny.kachi.ai.adapter.`in`.scheduler

import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 주기 키워드 확장 실행 설정.
 *
 * 확장 결과는 뉴스 요약과 달리 자주 바뀌지 않고 키워드마다 LLM을 호출하므로 기본 주기를 길게 둔다.
 */
@ConfigurationProperties(prefix = AiKeywordExpansionSchedulerProperties.PREFIX)
data class AiKeywordExpansionSchedulerProperties(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val maxExpansionsPerKeyword: Int,
) {
    companion object {
        const val PREFIX = "kachi.ai.scheduler.keyword-expansion"

        // @Scheduled는 애노테이션이라 주입된 properties 값을 참조할 수 없고 placeholder만 받는다.
        // 같은 설정 키를 두 곳에 문자열로 흩어두지 않도록 여기서 한 번만 선언한다.
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }

    init {
        require(maxExpansionsPerKeyword in 1..ExpandKeywordsCommand.MAX_EXPANSIONS_PER_KEYWORD) {
            "키워드별 최대 확장 개수는 1 이상 ${ExpandKeywordsCommand.MAX_EXPANSIONS_PER_KEYWORD} 이하여야 합니다"
        }
    }
}
