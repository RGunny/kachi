package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

/**
 * 채널 바인딩 조회 HTTP 응답을 읽은 결과. 404면 [notFound]가 true이고 [body]는 없다.
 */
internal data class UserServiceChannelBindingLookup(
    val notFound: Boolean,
    val body: UserServiceApiResponse<UserServiceChannelBindingResponse>?,
) {
    companion object {
        val NOT_FOUND = UserServiceChannelBindingLookup(notFound = true, body = null)

        fun found(body: UserServiceApiResponse<UserServiceChannelBindingResponse>): UserServiceChannelBindingLookup {
            return UserServiceChannelBindingLookup(notFound = false, body = body)
        }
    }
}
