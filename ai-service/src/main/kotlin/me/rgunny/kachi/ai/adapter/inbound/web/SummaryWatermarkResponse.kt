package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.watermark.model.SummaryWatermarkLag
import java.time.Instant

data class SummaryWatermarkResponse(
    val targetType: String,
    val position: Instant,
    val lagSeconds: Long,
    val updatedAt: Instant
) {
    companion object {

        fun from(lag: SummaryWatermarkLag): SummaryWatermarkResponse {
            return SummaryWatermarkResponse(
                targetType = lag.targetType.name,
                position = lag.position,
                lagSeconds = lag.lagSeconds,
                updatedAt = lag.updatedAt
            )
        }
    }
}
