package me.rgunny.kachi.notification.routing.application.service

/**
 * 알림 라우팅 정책. requester는 발행하는 모든 요청에 실리는 요청자 이름이다.
 */
data class RoutingPolicy(
    val requester: String,
) {
    init {
        require(requester.isNotBlank()) { "requester must not be blank" }
    }
}
