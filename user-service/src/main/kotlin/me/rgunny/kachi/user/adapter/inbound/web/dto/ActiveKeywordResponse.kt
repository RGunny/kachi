package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListActiveKeywordResult

/**
 * 활성 키워드 internal API 응답.
 *
 * `name`은 `canonicalKey`와 같은 값이다.
 * 이 API의 소비자가 `name`으로 수집·요약·조회를 이어가므로 정규화 값을 실어 뒷단에서 다시 정규화하지 않게 한다.
 */
data class ActiveKeywordResponse(
    val keywordId: String,
    val canonicalKey: String,
    val displayName: String,
    val name: String
) {
    companion object {
        fun from(result: ListActiveKeywordResult): ActiveKeywordResponse {
            return ActiveKeywordResponse(
                keywordId = result.keywordId.value.toString(),
                canonicalKey = result.canonicalKey,
                displayName = result.displayName,
                name = result.canonicalKey
            )
        }
    }
}
