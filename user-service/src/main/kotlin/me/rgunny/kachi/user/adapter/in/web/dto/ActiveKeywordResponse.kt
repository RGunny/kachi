package me.rgunny.kachi.user.adapter.`in`.web.dto

import me.rgunny.kachi.user.application.port.`in`.ListActiveKeywordResult

data class ActiveKeywordResponse(
    val name: String
) {
    companion object {
        fun from(result: ListActiveKeywordResult): ActiveKeywordResponse {
            return ActiveKeywordResponse(name = result.name)
        }
    }
}
