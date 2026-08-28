package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

/**
 * 수신 주소 조회 결과를 어디서 결정했는지 나타낸다. 지표의 source tag 근거다.
 */
enum class RecipientResolveSource {
    CACHE,
    USER_SERVICE,
}
