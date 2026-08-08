package me.rgunny.kachi.user.application.port.inbound.keyword

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListActiveKeywordResult

interface ListActiveKeywordsUseCase {

    fun listActiveKeywords(): List<ListActiveKeywordResult>
}
