package me.rgunny.kachi.collector.adapter.inbound.outbox

import java.time.Duration

/**
 * outbox relay 실행기와 scheduler가 보는 실행 설정.
 *
 * `kachi.collector.outbox.relay`
 */
data class CollectorOutboxRelaySettings(
    val publisherId: String,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val batchSize: Int,
    val publishingVisibilityTimeout: Duration
) {
    companion object {
        const val PREFIX = "kachi.collector.outbox.relay"

        // @Scheduled 애노테이션 인자용 placeholder 식(상수 문자열만 허용)
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }
}
