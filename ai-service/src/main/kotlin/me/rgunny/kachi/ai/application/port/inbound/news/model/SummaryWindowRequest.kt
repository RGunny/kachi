package me.rgunny.kachi.ai.application.port.inbound.news.model

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
 *
 * sealed로 둔 이유는 두 요청이 담는 값이 다르고(지정 구간 vs 정책 값), 요청 종류마다
 * watermark를 읽는지/전진시키는지가 갈리기 때문이다. 한 타입으로 합치면
 * "구간도 지정하고 watermark도 전진시키는" 조합이 표현 가능해진다.
 *
 * 컴파일 강제 지점은 `SummarizeNewsService`의 두 when이다.
 * - `loadWatermark`: watermark를 읽을지 결정
 * - `resolveWindow`: 구간을 계산하는 방식 결정
 *
 * 요청 종류가 늘어나면 두 곳이 함께 컴파일되지 않으므로, watermark 읽기와 구간 계산 규칙이
 * 서로 어긋난 채 추가될 수 없다.
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
