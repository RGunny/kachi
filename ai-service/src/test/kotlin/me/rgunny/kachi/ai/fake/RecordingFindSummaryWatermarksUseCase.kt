package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.watermark.FindSummaryWatermarksUseCase
import me.rgunny.kachi.ai.application.port.inbound.watermark.model.FindSummaryWatermarksResult
import me.rgunny.kachi.ai.application.port.inbound.watermark.model.SummaryWatermarkLag

/**
 * 지정한 진행 지점 목록을 돌려주는 watermark 조회 유스케이스 fake.
 */
class RecordingFindSummaryWatermarksUseCase : FindSummaryWatermarksUseCase {
    var invokeCount = 0
    var watermarks: List<SummaryWatermarkLag> = emptyList()

    override suspend fun find(): FindSummaryWatermarksResult {
        invokeCount += 1

        return FindSummaryWatermarksResult(watermarks)
    }
}
