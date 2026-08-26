package me.rgunny.kachi.ai.application.port.inbound.watermark.model

data class FindSummaryWatermarksResult(
    val watermarks: List<SummaryWatermarkLag>
)
