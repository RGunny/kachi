package me.rgunny.kachi.user.application.port.inbound.internal

import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolvedChannelBindingResult

/**
 * 수신자와 채널로 수신 주소를 푼다.
 *
 * 발송 직전에 호출되며 ACTIVE 바인딩만 주소를 돌려준다.
 */
interface ResolveChannelBindingUseCase {

    fun resolve(query: ResolveChannelBindingQuery): ResolvedChannelBindingResult
}
