package me.rgunny.kachi.user.application.port.`in`

interface ListActiveKeywordsUseCase {

    fun listActiveKeywords(): List<ListActiveKeywordResult>
}
