package me.rgunny.kachi.user.application.port.inbound.keyword.model

import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId

/**
 * 활성 키워드 한 건. 뒷단이 join key로 쓰는 [canonicalKey]와 사람에게 보여줄 [displayName]을 함께 낸다.
 */
data class ListActiveKeywordResult(
    val keywordId: KeywordId,
    val canonicalKey: String,
    val displayName: String
) {

    companion object {

        fun from(keyword: Keyword): ListActiveKeywordResult {
            return ListActiveKeywordResult(
                keywordId = keyword.id,
                canonicalKey = keyword.canonicalKey.value,
                displayName = keyword.displayName.value
            )
        }
    }
}
