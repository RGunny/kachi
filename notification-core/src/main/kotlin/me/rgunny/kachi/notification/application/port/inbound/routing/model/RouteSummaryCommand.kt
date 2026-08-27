package me.rgunny.kachi.notification.application.port.inbound.routing.model

/**
 * 요약 라우팅 입력 모델. 본문은 어댑터가 이미 렌더링한 텍스트다.
 */
data class RouteSummaryCommand(
    val summaryId: String,
    val keyword: String,
    val message: String,
)
