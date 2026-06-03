package me.rgunny.kachi.ai.adapter.out.keyword

data class UserServiceApiResponse<T>(
    val success: Boolean,
    val data: T?
)

data class UserServiceActiveKeywordResponse(
    val name: String
)
