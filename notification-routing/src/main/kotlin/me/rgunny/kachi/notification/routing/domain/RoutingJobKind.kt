package me.rgunny.kachi.notification.routing.domain

/**
 * routing job이 알림을 보내는 대상의 종류.
 */
enum class RoutingJobKind {
    SUMMARY,    // 키워드 구독자 x 채널
    ADMIN,      // 설정된 관리자 수신처
}
