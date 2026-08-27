package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 라우팅한 알림의 requestId를 만든다.
 *
 * (이벤트, 대상)의 순수 함수라서 어느 지점에서 중단됐다 다시 라우팅해도 같은 값이 나오고,
 * 접수의 requestId 멱등이 중복 알림을 막는다.
 */
object RoutingRequestId {

    fun forSummary(summaryId: String, userId: String, channel: NotificationChannel): String {
        return "sum:$summaryId:u:$userId:c:${channel.name}"
    }

    fun forAdmin(eventKey: String, channel: NotificationChannel): String {
        return "adm:$eventKey:c:${channel.name}"
    }
}
