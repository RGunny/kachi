package me.rgunny.kachi.ai.application.port.inbound.watermark

import me.rgunny.kachi.ai.application.port.inbound.watermark.model.FindSummaryWatermarksResult

/**
 * 요약 진행 지점과 현재 시각까지 벌어진 폭을 조회한다.
 */
interface FindSummaryWatermarksUseCase {

    suspend fun find(): FindSummaryWatermarksResult
}
