package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.user.model.GetUserResult
import me.rgunny.kachi.user.application.port.inbound.user.model.RegisterUserResult
import java.time.Instant

data class UserResponse(
    val id: String,
    val email: String,
    val nickname: String,
    val status: String,
    val role: String,
    val authProvider: String,
    val registeredAt: Instant
) {

    companion object {

        fun from(result: RegisterUserResult): UserResponse {
            return UserResponse(
                id = result.id.value.toString(),
                email = result.email,
                nickname = result.nickname,
                status = result.status.name,
                role = result.role.name,
                authProvider = result.authProvider.name,
                registeredAt = result.registeredAt
            )
        }

        fun from(result: GetUserResult): UserResponse {
            return UserResponse(
                id = result.id.value.toString(),
                email = result.email,
                nickname = result.nickname,
                status = result.status.name,
                role = result.role.name,
                authProvider = result.authProvider.name,
                registeredAt = result.registeredAt
            )
        }
    }
}
