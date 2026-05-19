package me.rgunny.kachi.user.adapter.`in`.web.dto

import jakarta.validation.constraints.Size

data class UpdateKeywordRequest(

    @field:Size(max = 100)
    val name: String? = null,

    val enabled: Boolean? = null
)
