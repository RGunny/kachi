package me.rgunny.kachi.ai.application.service.watermark

import me.rgunny.kachi.ai.application.port.inbound.watermark.FindSummaryWatermarksUseCase
import me.rgunny.kachi.ai.application.port.inbound.watermark.model.FindSummaryWatermarksResult
import me.rgunny.kachi.ai.application.port.inbound.watermark.model.SummaryWatermarkLag
import me.rgunny.kachi.ai.application.port.outbound.persistence.SummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 대상 종류마다 저장된 진행 지점을 읽어 조회 시점까지 벌어진 폭과 함께 돌려준다.
 *
 * 저장된 적 없는 대상 종류는 결과에서 뺀다.
 * 값이 없다는 것은 아직 아무 구간도 처리하지 않았다는 뜻이라 폭 0으로 표현하면 거짓이 된다.
 */
@Service
class FindSummaryWatermarksService(
    private val summaryWatermarkPersistencePort: SummaryWatermarkPersistencePort,
    private val clock: Clock
) : FindSummaryWatermarksUseCase {

    override suspend fun find(): FindSummaryWatermarksResult {
        val now = Instant.now(clock)

        val watermarks = AiRunTargetType.entries
            .mapNotNull { summaryWatermarkPersistencePort.findBy(it) }
            .map { lagOf(it, now) }

        return FindSummaryWatermarksResult(watermarks)
    }

    private fun lagOf(watermark: SummaryWatermark, now: Instant): SummaryWatermarkLag {
        return SummaryWatermarkLag(
            targetType = watermark.targetType,
            position = watermark.position,
            lagSeconds = Duration.between(watermark.position, now).seconds,
            updatedAt = watermark.updatedAt
        )
    }
}
