package me.rgunny.kachi.notification.routing.application.port.inbound.routing

import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteAdminCommand
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationResult

/**
 * 운영 이벤트를 설정된 관리자 수신처 채널로 라우팅하는 유스케이스.
 *
 * 구독 조회 대신 정책의 관리자 수신처를 쓴다는 점 외에는 요약 라우팅과 같은 규칙을 따른다.
 */
interface RouteAdminNotificationUseCase {

    suspend fun routeAdmin(command: RouteAdminCommand): RouteNotificationResult
}
