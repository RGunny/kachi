package me.rgunny.kachi.ai.adapter.inbound.scheduler

import java.time.Duration

/**
 * 키워드 확장 scheduler가 보는 실행 설정.
 *
 * `kachi.ai.scheduler.keyword-expansion`
 */
data class AiKeywordExpansionSchedulerSettings(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val maxExpansionsPerKeyword: Int
) {
    companion object {
        const val PREFIX = "kachi.ai.scheduler.keyword-expansion"

        // @Scheduled 애노테이션 인자용 placeholder 식(상수 문자열만 허용)
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }
}
