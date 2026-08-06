package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.out.persistence.SummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark

/**
 * 대상 종류당 한 건만 유지하는 in-memory watermark 저장소.
 *
 * saveCount는 전진하지 않아야 하는 실행이 실제로 저장을 시도하지 않았는지 확인하는 데 쓴다.
 */
class FakeSummaryWatermarkPersistencePort : SummaryWatermarkPersistencePort {
    val watermarks: MutableMap<AiRunTargetType, SummaryWatermark> = mutableMapOf()
    var saveCount: Int = 0

    override suspend fun findBy(targetType: AiRunTargetType): SummaryWatermark? {
        return watermarks[targetType]
    }

    override suspend fun save(watermark: SummaryWatermark): SummaryWatermark {
        saveCount += 1
        watermarks[watermark.targetType] = watermark

        return watermark
    }
}
