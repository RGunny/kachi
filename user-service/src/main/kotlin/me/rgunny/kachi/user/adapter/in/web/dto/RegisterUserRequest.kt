package me.rgunny.kachi.user.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import me.rgunny.kachi.user.domain.AuthProvider
import jakarta.validation.constraints.Email as EmailFormat

data class RegisterUserRequest(

    @field:NotBlank
    @field:EmailFormat
    val email: String,

    @field:NotBlank
    val nickname: String,

    val authProvider: AuthProvider
)
