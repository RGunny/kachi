package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.KeywordId

data class UpdateKeywordCommand(
    val keywordId: KeywordId,
    val name: String? = null,
    val enabled: Boolean? = null
) {

    init {
        require(name != null || enabled != null) { "수정할 키워드 값이 필요합니다" }
    }
}
