package me.rgunny.kachi.notification.domain

enum class NotificationChannel {

    SLACK,
    DISCORD,
    TELEGRAM,

    // 실발송 X, 더미서버 연동채널
    SMS,
    KAKAO,
    EMAIL
    ;

}
