package me.rgunny.kachi.notification.application.port.inbound.routing

import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteSummaryCommand

/**
 * 요약을 키워드 구독자가 정한 채널로 라우팅하는 유스케이스.
 *
 * 책임:
 * - eventKey 기준으로 한 번만 라우팅한다 (RoutingJob)
 * - 구독자 목록을 조회해 대상마다 결정적 requestId로 알림을 접수한다
 * - 중단됐던 job은 이어서 진행한다
 */
interface RouteSummaryNotificationUseCase {

    suspend fun routeSummary(command: RouteSummaryCommand): RouteNotificationResult
}
