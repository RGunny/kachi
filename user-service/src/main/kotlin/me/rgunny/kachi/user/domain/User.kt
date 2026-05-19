package me.rgunny.kachi.user.domain

import java.security.AuthProvider
import java.util.UUID


class User(
    val id: UUID,
    val email: String,
    val nickname: String,
    val status: UserStatus,
    val role: UserRole,
    val authProvider: AuthProvider
){


}