package me.rgunny.kachi.user.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다")
    val refreshToken: String
)
