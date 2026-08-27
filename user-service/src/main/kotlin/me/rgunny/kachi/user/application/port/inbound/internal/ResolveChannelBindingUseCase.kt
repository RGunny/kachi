package me.rgunny.kachi.user.application.port.inbound.internal

import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolvedChannelBindingResult

/**
 * 수신처 참조를 실제 주소로 푼다.
 *
 * 발송 직전에 호출되며 ACTIVE 바인딩만 주소를 돌려준다.
 */
interface ResolveChannelBindingUseCase {

    fun resolve(query: ResolveChannelBindingQuery): ResolvedChannelBindingResult
}
