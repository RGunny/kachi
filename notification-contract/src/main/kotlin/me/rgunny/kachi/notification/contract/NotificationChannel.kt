package me.rgunny.kachi.notification.contract

/**
 * 서비스 간 알림 요청 메시지에서 사용하는 채널 계약.
 *
 * notification-core의 domain enum과 이름을 맞추되, contract는 core에 의존하지 않는다.
 */
enum class NotificationChannel {
    SLACK,
    DISCORD,
    TELEGRAM,
    SMS,
    KAKAO,
    EMAIL,
}
