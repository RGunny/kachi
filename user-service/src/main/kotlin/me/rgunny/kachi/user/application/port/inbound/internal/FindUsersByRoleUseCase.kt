package me.rgunny.kachi.user.application.port.inbound.internal

import me.rgunny.kachi.user.application.port.inbound.internal.model.FindUsersByRoleQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.UserChannelsResult

/**
 * 역할로 수신자 목록을 찾는다.
 *
 * 특정 역할의 사용자 전원에게 보내는 알림(키워드 격리 등)의 라우팅이 호출한다.
 * ACTIVE 사용자 중 ACTIVE 바인딩이 하나 이상인 사용자만 돌려준다.
 */
interface FindUsersByRoleUseCase {

    fun findUsersByRole(query: FindUsersByRoleQuery): List<UserChannelsResult>
}
