package me.rgunny.kachi.user.application.port.inbound.keyword

import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordsQuery

/** 관심 키워드 목록 조회 유스케이스를 외부 입력 어댑터에 제공하는 포트 */
interface ListKeywordsUseCase {

    fun list(query: ListKeywordsQuery): List<ListKeywordResult>
}
