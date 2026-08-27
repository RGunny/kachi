package me.rgunny.kachi.user.application.port.inbound.binding

import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.application.port.inbound.binding.model.ListChannelBindingsQuery

/**
 * 사용자의 채널 바인딩 목록.
 * 주소는 마스킹한 값만 돌려준다.
 */
interface ListChannelBindingsUseCase {

    fun list(query: ListChannelBindingsQuery): List<ChannelBindingResult>
}
