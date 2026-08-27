package me.rgunny.kachi.notification.routing.application.port.inbound.routing.model

/**
 * 관리자 알림 라우팅 입력 모델. eventKey는 원 이벤트를 식별하는 값이다.
 */
data class RouteAdminCommand(
    val eventKey: String,
    val keyword: String,
    val message: String,
)
