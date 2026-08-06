package me.rgunny.kachi.ai.domain.watermark

import java.time.Duration
import java.time.Instant

/**
 * 한 번의 실행이 처리할 뉴스 수집 구간.
 *
 * 구간의 축은 collector의 `collectedAt`이다. 뉴스 조회가 이 축을 쓰므로 watermark도 같은 축에서 관리한다.
 *
 * skippedFrom은 maxLookback 하한에 걸려 이번 실행이 건너뛴 구간의 시작이다.
 * 건너뛴 구간이 조용히 사라지지 않도록 값으로 들고 다니며, 로그와 경고에 그대로 쓴다.
 */
data class SummaryWindow(
    val from: Instant,
    val to: Instant,
    val skippedFrom: Instant?
) {
    val truncated: Boolean
        get() = skippedFrom != null

    companion object {

        /**
         * watermark에서 이어받는 window를 계산한다.
         *
         * ```text
         * to   = now
         * from = max(watermark - overlap, to - maxLookback)
         * ```
         *
         * overlap은 watermark에서 뒤로 물러나는 폭으로, 늦게 도착한 뉴스를 흡수한다.
         * 겹쳐 읽어 생기는 중복 요약은 newsHash 재사용이 막으므로 항상 보수적으로(뒤로) 잡는다.
         */
        fun resolve(
            watermark: Instant?,
            now: Instant,
            overlap: Duration,
            maxLookback: Duration
        ): SummaryWindow {
            require(!overlap.isNegative) { "요약 window overlap은 음수일 수 없습니다" }
            require(!maxLookback.isZero && !maxLookback.isNegative) { "요약 window maxLookback은 0보다 커야 합니다" }

            // 1. watermark가 없는 최초 기동에서는 처리한 구간이 없으므로 현재 시각에서 overlap만큼만 되돌아본다.
            val startedFrom = (watermark ?: now).minus(overlap)

            // 2. 장기 정지 후 한 window가 무한정 커지지 않도록 from에 하한을 둔다.
            val lowerBound = now.minus(maxLookback)

            // 3. 시각 오차나 수동 수정으로 watermark가 미래에 있어도 빈 구간이 되지 않도록 to를 넘지 못하게 막는다.
            val from = maxOf(startedFrom, lowerBound).coerceAtMost(now)

            return SummaryWindow(
                from = from,
                to = now,
                skippedFrom = startedFrom.takeIf { it.isBefore(lowerBound) }
            )
        }
    }
}
