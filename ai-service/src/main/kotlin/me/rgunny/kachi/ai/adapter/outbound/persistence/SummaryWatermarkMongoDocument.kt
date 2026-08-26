package me.rgunny.kachi.ai.adapter.outbound.persistence

import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * 요약 진행 지점의 MongoDB 문서 표현.
 *
 * 대상 종류별로 한 건만 존재하므로 targetType 자체를 `_id`로 쓴다.
 * 별도 unique index 없이 중복 문서를 구조적으로 막고, 운영자가 진행 지점을 과거로 되돌려
 * 지나간 구간을 다시 처리시킬 때도 수정할 문서가 한 건으로 정해진다.
 */
@Document(collection = "ai_summary_watermarks")
data class SummaryWatermarkMongoDocument(
    @Id
    val targetType: String,
    val position: Instant,
    val updatedAt: Instant
) {

    fun toDomain(): SummaryWatermark {
        return SummaryWatermark.restore(
            targetType = AiRunTargetType.valueOf(targetType),
            position = position,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(watermark: SummaryWatermark): SummaryWatermarkMongoDocument {
            return SummaryWatermarkMongoDocument(
                targetType = watermark.targetType.name,
                position = watermark.position,
                updatedAt = watermark.updatedAt
            )
        }
    }
}
