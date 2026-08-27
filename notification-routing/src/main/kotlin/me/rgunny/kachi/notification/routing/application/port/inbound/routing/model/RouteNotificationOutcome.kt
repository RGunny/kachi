package me.rgunny.kachi.notification.routing.application.port.inbound.routing.model

enum class RouteNotificationOutcome {
    ROUTED,     // 이번 호출에서 라우팅했다 (대상 0건 포함)
    SKIPPED,    // 이미 COMPLETED된 job이라 라우팅하지 않았다
}
