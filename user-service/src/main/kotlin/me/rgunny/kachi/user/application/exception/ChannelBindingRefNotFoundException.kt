package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.ChannelBindingId

/**
 * 참조가 가리키는 바인딩이 없다.
 */
class ChannelBindingRefNotFoundException(
    val ref: ChannelBindingId
) : RuntimeException("채널 바인딩을 찾을 수 없습니다: ref=${ref.value}")
