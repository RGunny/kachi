package me.rgunny.kachi.notification.application.port.inbound.routing.model

import me.rgunny.kachi.notification.domain.RoutingJobId

/**
 * 라우팅 결과. 카운트는 이번 호출이 아니라 job에 기록된 최종값이다.
 */
data class RouteNotificationResult(
    val jobId: RoutingJobId,
    val outcome: RouteNotificationOutcome,
    val targetCount: Int,
    val acceptedCount: Int,
    val duplicatedCount: Int,
)
