package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.UserRole

/**
 * 수신자를 찾을 역할.
 */
data class FindUsersByRoleQuery(
    val role: UserRole
)
