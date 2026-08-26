package me.rgunny.kachi.user.application.port.inbound.internal.model

/**
 * 수신자를 찾을 키워드.
 * 활성 키워드 API가 내보낸 `name`(canonicalKey)을 그대로 받는다.
 */
data class FindSubscribersQuery(
    val keyword: String
)
