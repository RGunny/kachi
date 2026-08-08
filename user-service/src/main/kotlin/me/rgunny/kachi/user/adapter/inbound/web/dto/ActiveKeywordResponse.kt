package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListActiveKeywordResult

data class ActiveKeywordResponse(
    val name: String
) {
    companion object {
        fun from(result: ListActiveKeywordResult): ActiveKeywordResponse {
            return ActiveKeywordResponse(name = result.name)
        }
    }
}
