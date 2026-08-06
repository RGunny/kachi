package me.rgunny.kachi.ai.application.port.`in`.news

import java.time.Duration
import java.time.Instant

/**
 * 이번 실행이 처리할 구간을 어떻게 정할지에 대한 요청.
 *
 * watermark는 저장된 상태라 조회에 출력 포트가 필요하다. 진입점(scheduler, internal API)이 직접 읽지 않고
 * 정책 값만 실어 보내면, 상태 접근은 application 안에 남고 설정 소유는 진입점에 남는다.
 *
 * 어느 쪽을 골랐는지가 watermark 전진 여부를 함께 결정한다. 임의 구간을 지정한 수동 실행이
 * watermark를 움직이면 지정하지 않은 구간까지 처리된 것으로 기록되므로, 전진은 [FromWatermark]에만 허용한다.
 */
sealed interface SummaryWindowRequest {

    /**
     * 구간을 직접 지정한다. watermark를 읽지도 전진시키지도 않는다.
     */
    data class Explicit(
        val from: Instant?,
        val to: Instant?
    ) : SummaryWindowRequest {
        init {
            if (from != null && to != null) {
                require(!from.isAfter(to)) { "뉴스 요약 시작 시각은 종료 시각보다 이후일 수 없습니다" }
            }
        }
    }

    /**
     * 저장된 watermark에서 이어받는다. 격리되지 않은 키워드가 모두 성공하면 watermark를 전진시킨다.
     */
    data class FromWatermark(
        val overlap: Duration,
        val maxLookback: Duration
    ) : SummaryWindowRequest {
        init {
            require(!overlap.isNegative) { "요약 window overlap은 음수일 수 없습니다" }
            require(!maxLookback.isZero && !maxLookback.isNegative) { "요약 window maxLookback은 0보다 커야 합니다" }
        }
    }
}
