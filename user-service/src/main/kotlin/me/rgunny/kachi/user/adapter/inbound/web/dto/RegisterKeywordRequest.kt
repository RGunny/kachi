package me.rgunny.kachi.user.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterKeywordRequest(

    @field:NotBlank
    @field:Size(max = 100)
    val name: String
)
