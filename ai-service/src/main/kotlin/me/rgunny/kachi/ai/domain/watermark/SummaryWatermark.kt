package me.rgunny.kachi.ai.domain.watermark

import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import java.time.Instant

/**
 * 요약 처리를 끝낸 지점.
 *
 * 다음 실행은 이 지점에서 window를 시작하므로, 배포/장애로 멈춰 있던 구간도 재기동 후 이어서 처리한다.
 * 자세한 결정 배경은 docs/decisions/020-ai-service-scheduler-실행-모델과-요약-window.md 를 참고한다.
 */
class SummaryWatermark private constructor(
    val targetType: AiRunTargetType,
    val position: Instant,
    val updatedAt: Instant
) {

    companion object {

        fun initial(
            targetType: AiRunTargetType,
            position: Instant,
            updatedAt: Instant
        ): SummaryWatermark {
            return SummaryWatermark(
                targetType = targetType,
                position = position,
                updatedAt = updatedAt
            )
        }

        fun restore(
            targetType: AiRunTargetType,
            position: Instant,
            updatedAt: Instant
        ): SummaryWatermark {
            return SummaryWatermark(
                targetType = targetType,
                position = position,
                updatedAt = updatedAt
            )
        }
    }

    /**
     * 처리를 끝낸 지점을 요청 시각으로 옮긴다. 현재 지점보다 이후가 아니면 옮기지 않고 null을 반환한다.
     *
     * 정상 실행에서 요청 시각은 항상 현재 지점보다 나중이므로, 이 분기는 서버 시각이 뒤로 조정됐거나
     * watermark가 미래로 수동 수정된 어긋난 상태에서만 탄다.
     * 이미 처리한 구간을 다시 열 때는 운영자가 저장된 값을 직접 고친다. 뒤로 옮기는 연산은 두지 않는다.
     *
     * 이 비교는 객체가 들고 있는 값 기준이라, 여러 인스턴스가 동시에 저장하는 경합은 막지 못한다(ADR 020).
     */
    fun advanceTo(position: Instant, updatedAt: Instant): SummaryWatermark? {
        if (!position.isAfter(this.position)) {
            return null
        }

        return SummaryWatermark(
            targetType = targetType,
            position = position,
            updatedAt = updatedAt
        )
    }
}
